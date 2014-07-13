package cloudsync.helper;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
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
import org.apache.commons.lang3.StringUtils;

import cloudsync.connector.LocalFilesystemConnector;
import cloudsync.connector.RemoteConnector;
import cloudsync.model.DuplicateType;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.LinkType;

public class Structure {

	private final static Logger LOGGER = Logger.getLogger(Structure.class.getName());

	private final String name;

	private final LocalFilesystemConnector localConnection;
	private final RemoteConnector remoteConnection;
	private final Crypt crypt;

	private final Item root;
	private final List<Item> duplicates;
	private final DuplicateType duplicateFlag;
	private final LinkType followlinks;
	private final boolean nopermissions;
	private final int history;
	
	private Path cacheFilePath;
	private Path lockFilePath;
	private Path pidFilePath;

	private boolean isLocked = false;

	class Status {

		private int create = 0;
		private int update = 0;
		private int remove = 0;
		private int skip = 0;
	}

	public Structure(String name, final LocalFilesystemConnector localConnection, final RemoteConnector remoteConnection, final Crypt crypt, final DuplicateType duplicateFlag, final LinkType followlinks,
			final boolean nopermissions, final int history) {

		this.name = name;
		this.localConnection = localConnection;
		this.remoteConnection = remoteConnection;
		this.crypt = crypt;
		this.duplicateFlag = duplicateFlag;
		this.followlinks = followlinks;
		this.nopermissions = nopermissions;
		this.history = history;

		root = Item.getDummyRoot();
		duplicates = new ArrayList<Item>();
	}


	public void init(String cacheFile, String lockFile, String pidFile, boolean nocache, boolean forcestart) throws CloudsyncException {

		cacheFilePath = Paths.get(cacheFile.replace("{name}", name));
		lockFilePath = Paths.get(lockFile.replace("{name}", name));
		pidFilePath = Paths.get(pidFile.replace("{name}", name));

		if( !forcestart && Files.exists(pidFilePath, LinkOption.NOFOLLOW_LINKS) ){
			throw new CloudsyncException("Other job is running or previous job has crashed. If you are sure that no other job is running use the option '--forcestart'");
		}
		
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        String jvmName = bean.getName();
        long pid = Long.valueOf(jvmName.split("@")[0]);

        try {
			Files.write(pidFilePath, new Long(pid).toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			throw new CloudsyncException("Couldn't create '"+pidFilePath.toString()+"'");
		}
        
		if( Files.exists(lockFilePath, LinkOption.NOFOLLOW_LINKS) ){
			LOGGER.log(Level.WARNING,"Found an inconsistent cache file state. Possibly previous job has crashed. Force a cache file rebuild.");
			nocache = true;
		}
		
		if( !nocache && Files.exists(cacheFilePath, LinkOption.NOFOLLOW_LINKS) ){
			LOGGER.log(Level.INFO, "load structure from cache file");
			readCSVStructure(cacheFilePath);
		}
		else{
			LOGGER.log(Level.INFO, "load structure from remote server");
			createLock();
			readRemoteStructure(root);
		}
		releaseLock();
	}
	
	public void finalize() throws CloudsyncException{
		
		try {
			Files.delete(pidFilePath);
		} catch (IOException e) {
			throw new CloudsyncException("Couldn't remove '"+pidFilePath.toString()+"'");
		}
	}
	
	private void createLock() throws CloudsyncException{
		
		if( isLocked ) return;
		
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        String jvmName = bean.getName();
        long pid = Long.valueOf(jvmName.split("@")[0]);

        try {
			Files.write(lockFilePath, new Long(pid).toString().getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			throw new CloudsyncException("Couldn't create '"+lockFilePath.toString()+"'");
		}
        
        isLocked = true;
	}

	private void releaseLock() throws CloudsyncException{
		
		if( !isLocked ) return;
		
		try {
			Files.delete(lockFilePath);
		} catch (IOException e) {
			throw new CloudsyncException("Couldn't remove '"+lockFilePath.toString()+"'");
		}

		try {
			LOGGER.log(Level.INFO, "write structure to cache file");
			final PrintWriter out = new PrintWriter(cacheFilePath.toFile());
			final CSVPrinter csvOut = new CSVPrinter(out, CSVFormat.EXCEL);
			writeStructureToCSVPrinter(csvOut, root);
			out.close();
		} catch (final IOException e) {
			throw new CloudsyncException("Can't write cache file on '" + cacheFilePath.toString() + "'", e);
		}

		isLocked = false;
	}
	
	private void writeStructureToCSVPrinter(final CSVPrinter out, final Item parentItem) throws IOException {

		for (final Item child : parentItem.getChildren().values()) {
			out.printRecord(Arrays.asList(child.toArray()));
			if (child.isType(ItemType.FOLDER)) {
				writeStructureToCSVPrinter(out, child);
			}
		}
	}

	private void readCSVStructure(final Path cacheFilePath) throws CloudsyncException {

		final Map<String, Item> mapping = new HashMap<String, Item>();
		mapping.put("", root);

		try {
			final Reader in = new FileReader(cacheFilePath.toFile());
			final Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			for (final CSVRecord record : records) {

				final Item item = Item.fromCSV(record);
				final String childPath = Helper.trim(record.get(0), Item.SEPARATOR);
				final String parentPath = childPath.length() == item.getName().length() ? "" : StringUtils.removeEnd(FilenameUtils.getPath(childPath), Item.SEPARATOR);
				mapping.put(childPath, item);
				// System.out.println(parentPath+":"+item.getName());
				mapping.get(parentPath).addChild(item);
			}
		} catch (final IOException e) {

			throw new CloudsyncException("Can't read cache from file '" + cacheFilePath.toString() + "'", e);
		}
	}

	private void readRemoteStructure(final Item parentItem) throws CloudsyncException {

		final List<Item> childItems = remoteConnection.readFolder(this, parentItem);

		for (final Item childItem : childItems) {

			final Item existingChildItem = parentItem.getChildByName(childItem.getName());
			if (existingChildItem != null) {

				if (existingChildItem.getModifyTime().compareTo(childItem.getModifyTime()) < 0) {
					parentItem.addChild(childItem);
					duplicates.add(existingChildItem);
				} else {
					duplicates.add(childItem);
				}
			} else {
				parentItem.addChild(childItem);
			}

			if (childItem.isType(ItemType.FOLDER)) {
				readRemoteStructure(childItem);
			}
		}
	}

	public void clean() throws CloudsyncException {

		if (duplicates.size() > 0) {

			final List<Item> list = new ArrayList<Item>();
			for (final Item item : duplicates) {
				list.addAll(_flatRecursiveChildren(item));
			}
			for (final Item item : list) {
				localConnection.prepareUpload(this, item, duplicateFlag);
				LOGGER.log(Level.FINE, "restore " + item.getTypeName() + " '" + item.getPath() + "'");
				localConnection.upload(this, item, duplicateFlag, nopermissions);
			}

			Collections.reverse(list);
			for (final Item item : list) {
				LOGGER.log(Level.FINE, "clean " + item.getTypeName() + " '" + item.getPath() + "'");
				remoteConnection.remove(this, item);
			}
		}
	}

	public void list(String limitPattern) throws CloudsyncException {
		list(limitPattern,root);
	}

	private void list(final String limitPattern,final Item item) throws CloudsyncException {

		for (final Item child : item.getChildren().values()) {

			String path = child.getPath();
			if( limitPattern != null && !path.matches("^"+limitPattern+"$")) continue;

			LOGGER.log(Level.INFO, path);

			if (child.isType(ItemType.FOLDER)) {
				list(limitPattern,child);
			}
		}
	}

	public void restore(final boolean perform, final String limitPattern) throws CloudsyncException {
		restore(perform, limitPattern,root);
	}

	private void restore(final boolean perform, final String limitPattern,final Item item) throws CloudsyncException {

		for (final Item child : item.getChildren().values()) {
			
			String path = child.getPath();
			if( limitPattern != null && !path.matches("^"+limitPattern+"$")) continue;

			localConnection.prepareUpload(this, child, duplicateFlag);
			LOGGER.log(Level.FINE, "restore " + child.getTypeName() + " '" + path + "'");
			if (perform) {
				localConnection.upload(this, child, duplicateFlag, nopermissions);
			}

			if (child.isType(ItemType.FOLDER)) {
				restore(perform,limitPattern,child);
			}
		}
	}

	public void backup(final boolean perform) throws CloudsyncException {

		if (duplicates.size() > 0) {

			String message = "find " + duplicates.size() + " duplicate items:\n\n";
			final List<Item> list = new ArrayList<Item>();
			for (final Item item : duplicates) {
				list.addAll(_flatRecursiveChildren(item));
			}
			for (final Item item : list) {
				message += "  " + item.getRemoteIdentifier() + " - " + item.getPath() + "\n";
			}
			message += "\ntry to run with '--clean=<path>'";

			throw new CloudsyncException(message);
		}

		final Status status = new Status();

		backup(perform, root, status);
		
		boolean isChanged = isLocked;

		releaseLock();

		if (isChanged) {
			remoteConnection.cleanHistory(this, history);
		}

		final int total = status.create + status.update + status.skip;
		LOGGER.log(Level.INFO, "total items: " + (new Integer(total).toString()));
		LOGGER.log(Level.INFO, "created items: " + (new Integer(status.create).toString()));
		LOGGER.log(Level.INFO, "updated items: " + (new Integer(status.update).toString()));
		LOGGER.log(Level.INFO, "removed items: " + (new Integer(status.remove).toString()));
		LOGGER.log(Level.INFO, "skipped items: " + (new Integer(status.skip).toString()));
	}

	private void backup(final boolean perform, final Item remoteParentItem, final Status status) throws CloudsyncException {

		final Map<String, Item> unusedRemoteChildItems = remoteParentItem.getChildren();

		final List<Item> localChildItems = localConnection.readFolder(this, remoteParentItem, followlinks);

		for (final Item localChildItem : localChildItems) {

			Item remoteChildItem = remoteParentItem.getChildByName(localChildItem.getName());

			if (remoteChildItem == null) {
				remoteChildItem = localChildItem;
				remoteParentItem.addChild(remoteChildItem);
				LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
				if (perform) {
					createLock();
					remoteConnection.upload(this, remoteChildItem);
				}
				status.create++;
			} else {

				// echo item.getFileSize() +" "+
				// this.structure[key].getFileSize()+"\n";
				// echo item.getModifyTime() +" "+
				// this.structure[key].getModifyTime()+"\n";

				if (localChildItem.isTypeChanged(remoteChildItem)) {
					LOGGER.log(Level.FINE, "remove " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
					if (perform) {
						createLock();
						remoteConnection.remove(this, remoteChildItem);
					}
					status.remove++;

					remoteChildItem = localChildItem;
					remoteParentItem.addChild(remoteChildItem);
					LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
					if (perform) {
						createLock();
						remoteConnection.upload(this, localChildItem);
					}
					status.create++;
				}
				// check filesize and modify time
				else if (localChildItem.isMetadataChanged(remoteChildItem)) {
					final boolean isFiledataChanged = localChildItem.isFiledataChanged(remoteChildItem);
					remoteChildItem.update(localChildItem);
					LOGGER.log(Level.FINE, "update " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
					if (perform) {
						createLock();
						remoteConnection.update(this, remoteChildItem, isFiledataChanged);
					}
					status.update++;
				} else {
					status.skip++;
				}
			}

			unusedRemoteChildItems.remove(remoteChildItem.getName());

			if (remoteChildItem.isType(ItemType.FOLDER)) {
				backup(perform, remoteChildItem, status);
			}
		}

		for (final Item item : unusedRemoteChildItems.values()) {
			LOGGER.log(Level.FINE, "remove " + item.getTypeName() + " '" + item.getPath() + "'");
			remoteParentItem.removeChild(item);
			if (perform) {
				createLock();
				remoteConnection.remove(this, item);
			}
			status.remove++;
		}
	}

	private List<Item> _flatRecursiveChildren(final Item parentItem) {

		final List<Item> list = new ArrayList<Item>();
		list.add(parentItem);

		if (parentItem.isType(ItemType.FOLDER)) {
			for (final Item childItem : parentItem.getChildren().values()) {
				list.addAll(_flatRecursiveChildren(childItem));
			}
		}

		return list;
	}

	public Item getRootItem() {
		return root;
	}

	public String decryptText(final String text) throws IOException, CryptException {
		return crypt.decryptText(text);
	}

	public String encryptText(final String text) throws IOException, CryptException {
		return crypt.encryptText(text);
	}

	public byte[] decryptData(final InputStream stream) throws IOException, CryptException {
		return crypt.decryptData(stream);
	}

	public InputStream getLocalEncryptedBinaryStream(final Item item) throws IOException, CryptException {
		return crypt.getEncryptedBinary(localConnection.getFile(item), item);
	}

	public InputStream getRemoteEncryptedBinary(final Item item) throws IOException, CloudsyncException {
		return remoteConnection.get(this, item);
	}
}
