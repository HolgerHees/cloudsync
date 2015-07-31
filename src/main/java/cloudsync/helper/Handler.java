package cloudsync.helper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.dropbox.core.util.IOUtil;

import cloudsync.connector.LocalFilesystemConnector;
import cloudsync.connector.RemoteConnector;
import cloudsync.exceptions.CloudsyncException;
import cloudsync.model.ExistingBehaviorType;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.LinkType;
import cloudsync.model.PermissionType;
import cloudsync.model.RemoteItem;
import cloudsync.model.LocalStreamData;
import cloudsync.model.RemoteStreamData;
import cloudsync.model.SyncType;

public class Handler
{
	private final static Logger				LOGGER		= Logger.getLogger(Handler.class.getName());

	private final String					name;

	private final LocalFilesystemConnector	localConnection;
	private final RemoteConnector			remoteConnection;
	private final Crypt						crypt;

	private final Item						root;
	private final List<Item>				duplicates;
	private final ExistingBehaviorType		existingFlag;
	private final LinkType					followlinks;
	private final PermissionType			permissionType;

	private Path							cacheFilePath;
	private Path							lockFilePath;
	private Path							pidFilePath;
	private boolean							pidCleanup	= false;

	private boolean							isLocked	= false;

	class Status
	{
		private int	create	= 0;
		private int	update	= 0;
		private int	remove	= 0;
		private int	skip	= 0;
	}

	public Handler(String name, final LocalFilesystemConnector localConnection, final RemoteConnector remoteConnection, final Crypt crypt,
			final ExistingBehaviorType existingFlag, final LinkType followlinks, final PermissionType permissionType)
	{
		this.name = name;
		this.localConnection = localConnection;
		this.remoteConnection = remoteConnection;
		this.crypt = crypt;
		this.existingFlag = existingFlag;
		this.followlinks = followlinks;
		this.permissionType = permissionType;

		root = Item.getDummyRoot();
		duplicates = new ArrayList<Item>();
	}

	public void init(SyncType synctype, String cacheFile, String lockFile, String pidFile, boolean nocache, boolean forcestart) throws CloudsyncException
	{
		cacheFilePath = Paths.get(cacheFile.replace("{name}", name));
		lockFilePath = Paths.get(lockFile.replace("{name}", name));
		pidFilePath = Paths.get(pidFile.replace("{name}", name));

		if (synctype.checkPID())
		{
			if (!forcestart && Files.exists(pidFilePath, LinkOption.NOFOLLOW_LINKS))
			{
				throw new CloudsyncException(
						"Other job is running or previous job has crashed. If you are sure that no other job is running use the option '--forcestart'");
			}

			RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
			String jvmName = bean.getName();
			long pid = Long.valueOf(jvmName.split("@")[0]);

			try
			{
				Files.write(pidFilePath, new Long(pid).toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				pidCleanup = true;
			}
			catch (IOException e)
			{
				throw new CloudsyncException("Couldn't create '" + pidFilePath.toString() + "'");
			}
		}

		if (Files.exists(lockFilePath, LinkOption.NOFOLLOW_LINKS))
		{
			LOGGER.log(Level.WARNING,
					"Found an inconsistent cache file state. Possibly previous job has crashed or duplicate files was detected. Force a cache file rebuild.");
			nocache = true;
		}

		if (!nocache && Files.exists(cacheFilePath, LinkOption.NOFOLLOW_LINKS))
		{
			LOGGER.log(Level.INFO, "load structure from cache file");
			readCSVStructure(cacheFilePath);
		}
		else
		{
			LOGGER.log(Level.INFO, "load structure from remote server");
			createLock();
			readRemoteStructure(root);
		}
		releaseLock();
	}

	@Override
	public void finalize() throws CloudsyncException
	{
		try
		{
			if (pidCleanup) Files.delete(pidFilePath);
		}
		catch (IOException e)
		{
			throw new CloudsyncException("Couldn't remove '" + pidFilePath.toString() + "'");
		}
	}

	private void createLock() throws CloudsyncException
	{
		if (isLocked) return;

		try
		{
			if (!Files.exists(lockFilePath, LinkOption.NOFOLLOW_LINKS))
			{

				Files.createFile(lockFilePath);
			}
		}
		catch (IOException e)
		{
			throw new CloudsyncException("Couldn't create '" + lockFilePath.toString() + "'");
		}

		isLocked = true;
	}

	private void releaseLock() throws CloudsyncException
	{
		if (!isLocked || duplicates.size() > 0) return;

		try
		{
			Files.delete(lockFilePath);
		}
		catch (IOException e)
		{
			throw new CloudsyncException("Couldn't remove '" + lockFilePath.toString() + "'");
		}

		try
		{
			if (root.getChildren().size() > 0)
			{
				LOGGER.log(Level.INFO, "write structure to cache file");
				final PrintWriter out = new PrintWriter(cacheFilePath.toFile());
				final CSVPrinter csvOut = new CSVPrinter(out, CSVFormat.EXCEL);
				writeStructureToCSVPrinter(csvOut, root);
				out.close();
			}
		}
		catch (final IOException e)
		{
			throw new CloudsyncException("Can't write cache file on '" + cacheFilePath.toString() + "'", e);
		}

		isLocked = false;
	}

	private void writeStructureToCSVPrinter(final CSVPrinter out, final Item parentItem) throws IOException
	{
		for (final Item child : parentItem.getChildren().values())
		{
			out.printRecord(Arrays.asList(child.toCSVArray()));
			if (child.isType(ItemType.FOLDER))
			{
				writeStructureToCSVPrinter(out, child);
			}
		}
	}

	private void readCSVStructure(final Path cacheFilePath) throws CloudsyncException
	{
		final Map<String, Item> mapping = new HashMap<String, Item>();
		mapping.put("", root);

		try
		{
			final Reader in = new FileReader(cacheFilePath.toFile());
			final Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			for (final CSVRecord record : records)
			{

				final Item item = Item.fromCSV(record);
				final String childPath = Helper.trim(record.get(0), Item.SEPARATOR);
				final String parentPath = childPath.length() == item.getName().length() ? "" : StringUtils.removeEnd(FilenameUtils.getPath(childPath),
						Item.SEPARATOR);
				mapping.put(childPath, item);
				// System.out.println(parentPath+":"+item.getName());
				Item parent = mapping.get(parentPath);
				item.setParent(parent);
				parent.addChild(item);
			}
		}
		catch (final IOException e)
		{
			throw new CloudsyncException("Can't read cache from file '" + cacheFilePath.toString() + "'", e);
		}
	}

	private void readRemoteStructure(final Item parentItem) throws CloudsyncException
	{
		Map<ItemType, Integer> status = new HashMap<ItemType, Integer>();
		readRemoteStructure(parentItem, status);
		if (status.size() > 0) LOGGER.log(Level.INFO, formatRemoteStatus(status));
	}

	private void readRemoteStructure(final Item parentItem, Map<ItemType, Integer> status) throws CloudsyncException
	{
		final List<RemoteItem> childItems = remoteConnection.readFolder(this, parentItem);

		for (final RemoteItem childItem : childItems)
		{
			childItem.setParent(parentItem);

			final RemoteItem existingChildItem = (RemoteItem) parentItem.getChildByName(childItem.getName());
			if (existingChildItem != null)
			{
				LOGGER.log(Level.WARNING, "found duplicate: '" + childItem.getPath());
				String msg = "";
				if (childItem.getRemoteFilesize() != null) msg += " " + childItem.getRemoteFilesize();
				if (existingChildItem.getRemoteFilesize() != null) msg += " [" + existingChildItem.getRemoteFilesize() + "]";
				if (!StringUtils.isEmpty(msg)) LOGGER.log(Level.WARNING, "  size: " + msg);
				LOGGER.log(Level.WARNING, "  created: " + childItem.getRemoteCreationTime() + " [" + existingChildItem.getRemoteCreationTime() + "]");

				if (existingChildItem.getRemoteCreationTime().toMillis() > childItem.getRemoteCreationTime().toMillis())
				{
					parentItem.addChild(childItem);
					duplicates.add(existingChildItem);
				}
				else
				{
					duplicates.add(childItem);
				}

				putRemoteStatus(status, ItemType.DUPLICATE);

			}
			else
			{
				parentItem.addChild(childItem);
			}

			if (status.size() > 0) LOGGER.log(Level.INFO, "\r  " + formatRemoteStatus(status), true);

			putRemoteStatus(status, childItem.getType());

			if (childItem.isType(ItemType.FOLDER))
			{
				readRemoteStructure(childItem, status);
			}
		}
	}

	private void putRemoteStatus(Map<ItemType, Integer> status, ItemType type)
	{
		Integer count = status.get(type);
		if (count == null) count = new Integer(0);
		count++;
		status.put(type, count);
	}

	private String formatRemoteStatus(Map<ItemType, Integer> status)
	{
		List<String> typeStatus = new ArrayList<String>();
		for (ItemType type : ItemType.values())
		{
			Integer count = status.get(type);
			if (count == null) continue;
			typeStatus.add(count + " " + type.getName(count));
		}

		String lastType = typeStatus.remove(typeStatus.size() - 1);
		String message = StringUtils.join(typeStatus, ", ");
		if (message.length() > 0)
		{
			message += " and ";
		}
		message += lastType;
		return "found " + message;
	}

	private void checkDuplications() throws CloudsyncException
	{
		if (duplicates.size() > 0)
		{
			String message = "found " + duplicates.size() + " duplicate item" + (duplicates.size() == 1 ? "" : "s") + ":\n\n";
			final List<Item> list = new ArrayList<Item>();
			for (final Item item : duplicates)
			{
				list.addAll(_flatRecursiveChildren(item));
			}
			for (final Item item : list)
			{
				message += "  " + item.getRemoteIdentifier() + " - " + item.getPath() + "\n";
			}
			message += "\n  try to run with '--clean=<path>'";

			throw new CloudsyncException(message);
		}
	}

	private boolean checkPattern(String path, String[] includePatterns, String[] excludePatterns)
	{
		if (includePatterns != null)
		{
			boolean found = false;
			for (String includePattern : includePatterns)
			{
				if (path.matches("^" + includePattern + "$"))
				{
					found = true;
					break;
				}
			}
			if (!found) return false;
		}

		if (excludePatterns != null)
		{
			for (String excludePattern : excludePatterns)
			{
				if (path.matches("^" + excludePattern + "$"))
				{
					return false;
				}
			}
		}

		return true;
	}

	public void clean() throws CloudsyncException
	{
		if (duplicates.size() > 0)
		{
			final List<Item> list = new ArrayList<Item>();
			for (final Item item : duplicates)
			{
				list.addAll(_flatRecursiveChildren(item));
			}
			for (final Item item : list)
			{
				localConnection.prepareUpload(this, item, ExistingBehaviorType.RENAME);
				LOGGER.log(Level.FINE, "restore " + item.getTypeName() + " '" + item.getPath() + "'");
				localConnection.prepareParent(this, item);
				localConnection.upload(this, item, ExistingBehaviorType.RENAME, permissionType);
			}

			Collections.reverse(list);
			for (final Item item : list)
			{
				LOGGER.log(Level.FINE, "clean " + item.getTypeName() + " '" + item.getPath() + "'");
				remoteConnection.remove(this, item);
			}
			duplicates.clear();
			releaseLock();
		}
	}

	public void list(String[] includePatterns, String[] excludePatterns) throws CloudsyncException
	{
		checkDuplications();

		list(includePatterns, excludePatterns, root);
	}

	private void list(String[] includePatterns, String[] excludePatterns, final Item item) throws CloudsyncException
	{
		for (final Item child : item.getChildren().values())
		{
			String path = child.getPath();

			if (!checkPattern(path, includePatterns, excludePatterns)) continue;

			LOGGER.log(Level.INFO, path);

			if (child.isType(ItemType.FOLDER))
			{
				list(includePatterns, excludePatterns, child);
			}
		}
	}

	public void restore(final boolean perform, String[] includePatterns, String[] excludePatterns) throws CloudsyncException
	{
		checkDuplications();

		restore(perform, includePatterns, excludePatterns, root);
	}

	private void restore(final boolean perform, String[] includePatterns, String[] excludePatterns, final Item item) throws CloudsyncException
	{
		for (final Item child : item.getChildren().values())
		{
			String path = child.getPath();

			if (checkPattern(path, includePatterns, excludePatterns))
			{
				localConnection.prepareUpload(this, child, existingFlag);
				LOGGER.log(Level.FINE, "restore " + child.getTypeName() + " '" + path + "'");
				if (perform)
				{
					localConnection.upload(this, child, existingFlag, permissionType);
				}
			}

			if (child.isType(ItemType.FOLDER))
			{
				restore(perform, includePatterns, excludePatterns, child);
			}
		}
	}

	public void backup(final boolean perform, String[] includePatterns, String[] excludePatterns) throws CloudsyncException
	{
		checkDuplications();

		final Status status = new Status();

		backup(perform, includePatterns, excludePatterns, root, status);

		boolean isChanged = isLocked;

		releaseLock();

		if (isChanged)
		{
			remoteConnection.cleanHistory(this);
		}

		final int total = status.create + status.update + status.skip;
		LOGGER.log(Level.INFO, "total items: " + (new Integer(total).toString()));
		LOGGER.log(Level.INFO, "created items: " + (new Integer(status.create).toString()));
		LOGGER.log(Level.INFO, "updated items: " + (new Integer(status.update).toString()));
		LOGGER.log(Level.INFO, "removed items: " + (new Integer(status.remove).toString()));
		LOGGER.log(Level.INFO, "skipped items: " + (new Integer(status.skip).toString()));
	}

	private void backup(final boolean perform, String[] includePatterns, String[] excludePatterns, final Item remoteParentItem, final Status status)
			throws CloudsyncException
	{
		final Map<String, Item> unusedRemoteChildItems = remoteParentItem.getChildren();

		for (File localChildFile : localConnection.readFolder(remoteParentItem))
		{
			String filePath = localChildFile.getAbsolutePath();
			if (!checkPattern(filePath, includePatterns, excludePatterns)) continue;

			String backupPath = filePath;
			try
			{
				Item localChildItem = localConnection.getItem(localChildFile, followlinks);
				localChildItem.setParent(remoteParentItem);

				backupPath = localChildItem.getPath();

				Item remoteChildItem = remoteParentItem.getChildByName(localChildItem.getName());

				if (remoteChildItem == null)
				{
					remoteChildItem = localChildItem;
					LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + backupPath + "'");
					if (perform)
					{
						createLock();
						remoteConnection.upload(this, remoteChildItem);
					}
					remoteParentItem.addChild(remoteChildItem);
					status.create++;
				}
				else
				{
					if (remoteChildItem.isTypeChanged(localChildItem))
					{
						LOGGER.log(Level.FINE, "remove " + remoteChildItem.getTypeName() + " '" + backupPath + "'");
						if (perform)
						{
							createLock();
							remoteConnection.remove(this, remoteChildItem);
						}
						status.remove++;

						remoteChildItem = localChildItem;
						LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + backupPath + "'");
						if (perform)
						{
							createLock();
							remoteConnection.upload(this, remoteChildItem);
						}
						remoteParentItem.addChild(remoteChildItem);
						status.create++;
					}
					// check filesize and modify time
					else if (remoteChildItem.isMetadataChanged(localChildItem))
					{
						final boolean isFiledataChanged = localChildItem.isFiledataChanged(remoteChildItem);
						remoteChildItem.update(localChildItem);
						List<String> types = new ArrayList<String>();
						if (isFiledataChanged) types.add("data,attributes");
						else if (!isFiledataChanged) types.add("attributes");
						if (remoteChildItem.isMetadataFormatChanged()) types.add("format");
						LOGGER.log(Level.FINE, "update " + remoteChildItem.getTypeName() + " '" + backupPath + "' [" + StringUtils.join(types, ",") + "]");
						if (perform)
						{
							createLock();
							remoteConnection.update(this, remoteChildItem, isFiledataChanged);
						}
						status.update++;
					}
					else
					{
						status.skip++;
					}
				}

				try
				{
					// refresh Metadata
					Item _localChildItem = localConnection.getItem(localChildFile, followlinks);
					if (_localChildItem.isMetadataChanged(localChildItem))
					{
						LOGGER.log(Level.WARNING, localChildItem.getTypeName() + " '" + backupPath + "' was changed during update.");
					}
				}
				catch (NoSuchFileException e)
				{
					LOGGER.log(Level.WARNING, localChildItem.getTypeName() + " '" + backupPath + "' was removed during update.");
				}

				unusedRemoteChildItems.remove(remoteChildItem.getName());

				if (remoteChildItem.isType(ItemType.FOLDER))
				{
					backup(perform, includePatterns, excludePatterns, remoteChildItem, status);
				}
			}
			catch (NoSuchFileException e)
			{
				LOGGER.log(Level.WARNING, "skip '" + backupPath + "'. does not exists anymore.");
			}
		}

		for (final Item item : unusedRemoteChildItems.values())
		{
			LOGGER.log(Level.FINE, "remove " + item.getTypeName() + " '" + item.getPath() + "'");
			remoteParentItem.removeChild(item);
			if (perform)
			{
				createLock();
				remoteConnection.remove(this, item);
			}
			status.remove++;
		}
	}

	private List<Item> _flatRecursiveChildren(final Item parentItem)
	{

		final List<Item> list = new ArrayList<Item>();
		list.add(parentItem);

		if (parentItem.isType(ItemType.FOLDER))
		{
			for (final Item childItem : parentItem.getChildren().values())
			{
				list.addAll(_flatRecursiveChildren(childItem));
			}
		}

		return list;
	}

	public Item getRootItem()
	{
		return root;
	}

	public LocalStreamData getLocalProcessedBinary(final Item item) throws NoSuchFileException, CloudsyncException
	{
		LocalStreamData data = localConnection.getFileBinary(item);

		if (data != null && crypt != null ) data = crypt.encryptedBinary(item.getName(), data, item);

		return data;
	}

	public String getLocalProcessedMetadata(final Item item) throws CloudsyncException
	{
		String metadata = item.getMetadata(this);
		return crypt != null ? crypt.encryptText(metadata) : metadata;
	}

	public String getLocalProcessedTitle(final Item item) throws CloudsyncException
	{
		return crypt != null ? crypt.encryptText(item.getName()) : item.getName();
	}

	public RemoteItem initRemoteItem(String remoteIdentifier, boolean isFolder, String title, String metadata, Long remoteFilesize, FileTime remoteCreationtime)
			throws CloudsyncException
	{
		return Item.fromMetadata(remoteIdentifier, isFolder, title, metadata, remoteFilesize, remoteCreationtime);
	}

	public RemoteStreamData getRemoteProcessedBinary(Item item) throws IOException, CloudsyncException
	{
		InputStream stream = remoteConnection.get(this, item);
		
		if( crypt != null )
		{
			try
			{
				return new RemoteStreamData(stream,crypt.decryptData(stream));
			}
			catch(Exception e)
			{
				if( stream != null ) IOUtils.closeQuietly(stream);
				throw e;
			}
		}
		else
		{
			return new RemoteStreamData(null,stream);
		}
	}

	public String getProcessedText(final String text) throws CloudsyncException
	{
		return crypt != null ? crypt.decryptText(text) : text;
	}
}
