package cloudsync.model;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public class Item {

	public static String SEPARATOR = File.separator;

	private Item parent;

	private String name;
	private String remoteIdentifier;
	private ItemType type;
	private Long filesize;
	private FileTime creationtime;
	private FileTime modifytime;
	private FileTime accesstime;
	private String group;
	private String user;
	private Integer permissions;

	private Map<String, Item> children;

	public Item(final String name, final String remoteIdentifier, final ItemType type, final Long filesize, final FileTime creationtime, final FileTime modifytime, final FileTime accesstime,
			final String group, final String user, final Integer permissions) {

		this.name = name;
		this.remoteIdentifier = remoteIdentifier;
		this.type = type;
		this.filesize = filesize;
		this.creationtime = creationtime;
		this.modifytime = modifytime;
		this.accesstime = accesstime;
		this.group = group;
		this.user = user;
		this.permissions = permissions;

		if (this.type.equals(ItemType.FOLDER)) {
			children = new HashMap<String, Item>();
		}
	}

	public static Item getDummyRoot() {
		return new Item("", "", ItemType.FOLDER, null, null, null, null, null, null, null);
	}

	public static Item fromCSV(final CSVRecord values) {

		final String name = FilenameUtils.getName(values.get(0));
		final String remoteIndentifier = values.get(1);
		final ItemType type = ItemType.fromString(values.get(2));
		final Long filesize = StringUtils.isEmpty(values.get(3)) ? null : Long.parseLong(values.get(3));
		final FileTime creationtime = StringUtils.isEmpty(values.get(4)) ? null : FileTime.from(Long.parseLong(values.get(4)), TimeUnit.SECONDS);
		final FileTime modifytime = StringUtils.isEmpty(values.get(5)) ? null : FileTime.from(Long.parseLong(values.get(5)), TimeUnit.SECONDS);
		final FileTime accesstime = StringUtils.isEmpty(values.get(6)) ? null : FileTime.from(Long.parseLong(values.get(6)), TimeUnit.SECONDS);
		final String group = StringUtils.isEmpty(values.get(7)) ? null : values.get(7);
		final String user = StringUtils.isEmpty(values.get(8)) ? null : values.get(8);
		final Integer permissions = StringUtils.isEmpty(values.get(9)) ? null : Integer.parseInt(values.get(9));

		return new Item(name, remoteIndentifier, type, filesize, creationtime, modifytime, accesstime, group, user, permissions);
	}

	public String[] toArray() {

		return new String[] { getPath(), remoteIdentifier, type.toString(), filesize == null ? null : filesize.toString(),
				creationtime == null ? null : new Long(creationtime.to(TimeUnit.SECONDS)).toString(), modifytime == null ? null : new Long(modifytime.to(TimeUnit.SECONDS)).toString(),
				accesstime == null ? null : new Long(accesstime.to(TimeUnit.SECONDS)).toString(), group == null ? null : group, user == null ? null : user,
				permissions == null ? null : permissions.toString() };
	}

	public static RemoteItem fromMetadata(final String name, final String remoteIdentifier, final boolean isFolder, final String[] metadata, Long remoteFilesize, FileTime remoteCreationtime) {

		ItemType type;
		Long filesize = null;
		FileTime creationtime = null;
		FileTime modifytime = null;
		FileTime accesstime = null;
		String group = null;
		String user = null;
		Integer permissions = null;
		if (metadata != null) {
			type = ItemType.fromString(metadata[0]);
			filesize = StringUtils.isEmpty(metadata[1]) ? null : Long.parseLong(metadata[1]);
			creationtime = StringUtils.isEmpty(metadata[2]) ? null : FileTime.from(Long.parseLong(metadata[2]), TimeUnit.SECONDS);
			modifytime = StringUtils.isEmpty(metadata[3]) ? null : FileTime.from(Long.parseLong(metadata[3]), TimeUnit.SECONDS);
			accesstime = StringUtils.isEmpty(metadata[4]) ? null : FileTime.from(Long.parseLong(metadata[4]), TimeUnit.SECONDS);
			group = StringUtils.isEmpty(metadata[5]) ? null : metadata[5];
			user = StringUtils.isEmpty(metadata[6]) ? null : metadata[6];
			permissions = StringUtils.isEmpty(metadata[7]) ? null : Integer.parseInt(metadata[7]);
		} else {
			type = isFolder ? ItemType.FOLDER : ItemType.FILE;
		}

		RemoteItem item = new RemoteItem(name, remoteIdentifier, type, filesize, creationtime, modifytime, accesstime, group, user, permissions, remoteFilesize, remoteCreationtime);
		return item;
	}

	public String[] getMetadata() {

		return new String[] { type.toString(), filesize != null ? filesize.toString() : null, creationtime != null ? new Long(creationtime.to(TimeUnit.SECONDS)).toString() : null,
				modifytime != null ? new Long(modifytime.to(TimeUnit.SECONDS)).toString() : null, accesstime != null ? new Long(accesstime.to(TimeUnit.SECONDS)).toString() : null, group, user,
				permissions != null ? permissions.toString() : null };
	}

	public void setParent(final Item parent) {
		this.parent = parent;
	}

	public Item getParent() {
		return parent;
	}

	public void setRemoteIdentifier(final String remoteIdentifier) {
		this.remoteIdentifier = remoteIdentifier;
	}

	public void addChild(final Item child) {
		children.put(child.name, child);
	}

	public void removeChild(final Item child) {
		children.remove(child.name);
	}

	public Item getChildByName(final String name) {
		return children.get(name);
	}

	public Map<String, Item> getChildren() {
		return new HashMap<String, Item>(children);
	}

	public String getTypeName() {

		return type.getName();
	}

	public boolean isTypeChanged(final Item item) {
		return !type.equals(item.type);
	}

	public boolean isFiledataChanged(final Item item) {
		if (isChanged(filesize, item.filesize)) {
			return true;
		}
		if (isChanged(creationtime, item.creationtime)) {
			return true;
		}
		if (isChanged(modifytime, item.modifytime)) {
			return true;
		}
		/*
		 * if (isChanged(accesstime, item.accesstime)) { return true; }
		 */
		return false;
	}

	public boolean isMetadataChanged(final Item item) {

		if (isChanged(filesize, item.filesize)) {
			return true;
		}
		if (isChanged(creationtime, item.creationtime)) {
			return true;
		}
		if (isChanged(modifytime, item.modifytime)) {
			return true;
		}
		/*
		 * if (isChanged(accesstime, item.accesstime)) { return true; }
		 */
		if (isChanged(group, item.group)) {
			return true;
		}
		if (isChanged(user, item.user)) {
			return true;
		}
		if (isChanged(permissions, item.permissions)) {
			return true;
		}
		return false;
	}

	private boolean isChanged(final Object o1, final Object o2) {

		return (o1 == null) != (o2 == null) || (o1 != null && !o1.equals(o2));
	}

	public void update(final Item item) {
		type = item.type;
		filesize = item.filesize;
		modifytime = item.modifytime;
		creationtime = item.creationtime;
		group = item.group;
		user = item.user;
		permissions = item.permissions;
	}

	public String getPath() {
		String path = "";
		if (parent != null) {
			path += parent.getPath();
		}
		if (!path.isEmpty()) {
			path += Item.SEPARATOR;
		}
		path += name;
		return path;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getRemoteIdentifier() {
		return remoteIdentifier;
	}

	public boolean isType(final ItemType type) {
		return this.type.equals(type);
	}

	public String getGroup() {
		return group;
	}

	public String getUser() {
		return user;
	}

	public Integer getPermissions() {
		return permissions;
	}

	public Long getFilesize() {

		return filesize;
	}

	public FileTime getCreationTime() {

		return creationtime;
	}

	public FileTime getModifyTime() {

		return modifytime;
	}

	public FileTime getAccessTime() {

		return accesstime;
	}
}
