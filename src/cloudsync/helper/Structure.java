package cloudsync.helper;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
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

	private final LocalFilesystemConnector localConnection;
	private final RemoteConnector remoteConnection;
	private final Crypt crypt;

	private final Item root;
	private final List<Item> duplicates;
	private final DuplicateType duplicateFlag;
	private final LinkType followlinks;
	private final boolean nopermissions;
	private final int history;

	private enum ChangedType {
		UNCHANGED, REFRESHED, CHANGED
	}

	private ChangedType structureIsChanged = ChangedType.UNCHANGED;

	class Status {

		private int create = 0;
		private int update = 0;
		private int remove = 0;
		private int skip = 0;
	}

	public Structure(final LocalFilesystemConnector localConnection, final RemoteConnector remoteConnection, final Crypt crypt, final DuplicateType duplicate, final LinkType followlinks,
			final boolean nopermissions, final int history) {

		this.localConnection = localConnection;
		this.remoteConnection = remoteConnection;
		this.crypt = crypt;
		duplicateFlag = duplicate;
		this.followlinks = followlinks;
		this.nopermissions = nopermissions;
		this.history = history;

		root = Item.getDummyRoot();
		duplicates = new ArrayList<Item>();
	}

	public void buildStructureFromFile(final Path cacheFilePath) throws CloudsyncException {

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

	public void buildStructureFromRemoteConnection() throws CloudsyncException {
		structureIsChanged = ChangedType.REFRESHED;
		_walkRemoteStructure(root);
	}

	private void _walkRemoteStructure(final Item parentItem) throws CloudsyncException {

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
				_walkRemoteStructure(childItem);
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

	public void restore(final boolean perform) throws CloudsyncException {
		_restoreRemoteStructure(root, perform);
	}

	private void _restoreRemoteStructure(final Item item, final boolean perform) throws CloudsyncException {

		for (final Item child : item.getChildren().values()) {

			localConnection.prepareUpload(this, child, duplicateFlag);
			LOGGER.log(Level.FINE, "restore " + child.getTypeName() + " '" + child.getPath() + "'");
			if (perform) {
				localConnection.upload(this, child, duplicateFlag, nopermissions);
			}

			if (child.isType(ItemType.FOLDER)) {
				_restoreRemoteStructure(child, perform);
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

		_backupLocalStructure(root, perform, status);

		if (structureIsChanged.equals(ChangedType.CHANGED)) {
			remoteConnection.cleanHistory(this, history);
		}

		final int total = status.create + status.update + status.skip;
		LOGGER.log(Level.INFO, "total items: " + (new Integer(total).toString()));
		LOGGER.log(Level.INFO, "created items: " + (new Integer(status.create).toString()));
		LOGGER.log(Level.INFO, "updated items: " + (new Integer(status.update).toString()));
		LOGGER.log(Level.INFO, "removed items: " + (new Integer(status.remove).toString()));
		LOGGER.log(Level.INFO, "skipped items: " + (new Integer(status.skip).toString()));
	}

	private void _backupLocalStructure(final Item remoteParentItem, final boolean perform, final Status status) throws CloudsyncException {

		final Map<String, Item> unusedRemoteChildItems = remoteParentItem.getChildren();

		final List<Item> localChildItems = localConnection.readFolder(this, remoteParentItem, followlinks);

		for (final Item localChildItem : localChildItems) {

			Item remoteChildItem = remoteParentItem.getChildByName(localChildItem.getName());

			if (remoteChildItem == null) {
				remoteChildItem = localChildItem;
				remoteParentItem.addChild(remoteChildItem);
				LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
				if (perform) {
					remoteConnection.upload(this, remoteChildItem);
					structureIsChanged = ChangedType.CHANGED;
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
						remoteConnection.remove(this, remoteChildItem);
						structureIsChanged = ChangedType.CHANGED;
					}
					status.remove++;

					remoteChildItem = localChildItem;
					remoteParentItem.addChild(remoteChildItem);
					LOGGER.log(Level.FINE, "create " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
					if (perform) {
						remoteConnection.upload(this, localChildItem);
						structureIsChanged = ChangedType.CHANGED;
					}
					status.create++;
				}
				// check filesize and modify time
				else if (localChildItem.isMetadataChanged(remoteChildItem)) {
					final boolean isFiledataChanged = localChildItem.isFiledataChanged(remoteChildItem);
					remoteChildItem.update(localChildItem);
					LOGGER.log(Level.FINE, "update " + remoteChildItem.getTypeName() + " '" + remoteChildItem.getPath() + "'");
					if (perform) {
						remoteConnection.update(this, remoteChildItem, isFiledataChanged);
						structureIsChanged = ChangedType.CHANGED;
					}
					status.update++;
				} else {
					status.skip++;
				}
			}

			unusedRemoteChildItems.remove(remoteChildItem.getName());

			if (remoteChildItem.isType(ItemType.FOLDER)) {
				_backupLocalStructure(remoteChildItem, perform, status);
			}
		}

		for (final Item item : unusedRemoteChildItems.values()) {
			LOGGER.log(Level.FINE, "remove " + item.getTypeName() + " '" + item.getPath() + "'");
			remoteParentItem.removeChild(item);
			if (perform) {
				remoteConnection.remove(this, item);
				structureIsChanged = ChangedType.CHANGED;
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

	public void saveChangedStructureToFile(final Path cacheFilePath) throws CloudsyncException {

		if (structureIsChanged.equals(ChangedType.UNCHANGED)) {
			return;
		}

		LOGGER.log(Level.FINE, "update cache file.");
		try {
			final PrintWriter out = new PrintWriter(cacheFilePath.toFile());
			final CSVPrinter csvOut = new CSVPrinter(out, CSVFormat.EXCEL);
			_saveToStructureFile(csvOut, root);
			out.close();
		} catch (final IOException e) {
			throw new CloudsyncException("Can't write cache file on '" + cacheFilePath.toString() + "'", e);
		}
	}

	private void _saveToStructureFile(final CSVPrinter out, final Item parentItem) throws IOException {

		for (final Item child : parentItem.getChildren().values()) {

			out.printRecord(Arrays.asList(child.toArray()));

			if (child.isType(ItemType.FOLDER)) {
				_saveToStructureFile(out, child);
			}
		}
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
