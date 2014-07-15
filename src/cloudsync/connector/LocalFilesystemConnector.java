package cloudsync.connector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.CryptException;
import cloudsync.helper.Helper;
import cloudsync.helper.Structure;
import cloudsync.model.DuplicateType;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.LinkType;

public class LocalFilesystemConnector {

	private final static Logger LOGGER = Logger.getLogger(LocalFilesystemConnector.class.getName());

	private static Map<Integer, PosixFilePermission> toPermMapping = new HashMap<Integer, PosixFilePermission>();
	static {
		toPermMapping.put(0001, PosixFilePermission.OTHERS_EXECUTE);
		toPermMapping.put(0002, PosixFilePermission.OTHERS_WRITE);
		toPermMapping.put(0004, PosixFilePermission.OTHERS_READ);
		toPermMapping.put(0010, PosixFilePermission.GROUP_EXECUTE);
		toPermMapping.put(0020, PosixFilePermission.GROUP_WRITE);
		toPermMapping.put(0040, PosixFilePermission.GROUP_READ);
		toPermMapping.put(0100, PosixFilePermission.OWNER_EXECUTE);
		toPermMapping.put(0200, PosixFilePermission.OWNER_WRITE);
		toPermMapping.put(0400, PosixFilePermission.OWNER_READ);
	}

	private static Map<PosixFilePermission, Integer> fromPermMapping = new HashMap<PosixFilePermission, Integer>();

	private static Map<String, Boolean> group_state = new HashMap<String, Boolean>();
	private static Map<String, Boolean> user_state = new HashMap<String, Boolean>();

	private final String localPath;

	public LocalFilesystemConnector(final String path) {

		localPath = Item.SEPARATOR + Helper.trim(path, Item.SEPARATOR);

		for (final Integer key : toPermMapping.keySet()) {

			final PosixFilePermission perm = toPermMapping.get(key);
			fromPermMapping.put(perm, key);
		}
	}

	public void prepareUpload(final Structure structure, final Item item, final DuplicateType duplicateFlag) {

		if (!duplicateFlag.equals(DuplicateType.RENAME)) {
			return;
		}

		String path = localPath + Item.SEPARATOR + item.getPath();

		if (exists(Paths.get(path))) {

			int i = 0;
			while (exists(Paths.get(path + "+" + i))) {
				i++;
			}

			path += "+" + i;

			item.setName(FilenameUtils.getBaseName(path));
		}
	}

	public void prepareParent(Structure structure, Item item) throws CloudsyncException {

		if (item.getParent() != null) {

			Item parentItem = item.getParent();

			final Path parentPath = Paths.get(localPath + Item.SEPARATOR + parentItem.getPath());

			try {
				Files.createDirectories(parentPath);
			} catch (IOException e) {
				throw new CloudsyncException("Can't create " + parentItem.getTypeName() + " '" + parentItem.getPath() + "'", e);
			}
		}
	}

	public void upload(final Structure structure, final Item item, final DuplicateType duplicateFlag, final boolean nopermissions) throws CloudsyncException {

		final String _path = localPath + Item.SEPARATOR + item.getPath();

		final Path path = Paths.get(_path);

		// PosixFileAttributes attr = Files.readAttributes(file.toPath(),
		// PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

		if (exists(path)) {

			if (!duplicateFlag.equals(DuplicateType.UPDATE)) {
				throw new CloudsyncException("Item already '" + item.getPath() + "' exists. Try to specify another '--duplicate' behavior.");
			}

			if ((!item.isType(ItemType.FOLDER) || !isDir(path))) {

				try {
					Files.delete(path);
				} catch (final IOException e) {
					throw new CloudsyncException("Can't clear " + item.getTypeName() + " on '" + item.getPath() + "'", e);
				}
			}
		}

		if (item.isType(ItemType.FOLDER)) {

			if (!exists(path)) {

				try {
					Files.createDirectory(path);
				} catch (final IOException e) {
					throw new CloudsyncException("Can't create " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			}
		} else {

			if (item.getParent() != null) {

				final Path parentPath = Paths.get(localPath + Item.SEPARATOR + item.getParent().getPath());

				if (!isDir(parentPath)) {

					throw new CloudsyncException("Parent directory of " + item.getTypeName() + " '" + item.getPath() + "' is missing.");
				}
			}

			if (item.isType(ItemType.LINK)) {

				InputStream encryptedStream;
				try {

					encryptedStream = structure.getRemoteEncryptedBinary(item);
					final String link = new String(structure.decryptData(encryptedStream));
					Files.createSymbolicLink(path, Paths.get(link));

				} catch (final CryptException e) {

					throw new CloudsyncException("Can't decrypt " + item.getTypeName() + " '" + item.getPath() + "'", e);
				} catch (final IOException e) {

					throw new CloudsyncException("Unexpected error during local update of " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			} else if (item.isType(ItemType.FILE)) {

				InputStream encryptedStream;
				try {
					encryptedStream = structure.getRemoteEncryptedBinary(item);
					final byte[] data = structure.decryptData(encryptedStream);
					final FileOutputStream fos = new FileOutputStream(path.toFile());
					try {
						fos.write(data);
					} finally {
						fos.close();
					}
				} catch (final CryptException e) {

					throw new CloudsyncException("Can't decrypt " + item.getTypeName() + " '" + item.getPath() + "'", e);
				} catch (final IOException e) {

					throw new CloudsyncException("Unexpected error during local update of " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			} else {
				throw new CloudsyncException("Unsupported type " + item.getTypeName() + "' on '" + item.getPath() + "'");
			}
		}

		try {
			Files.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(item.getModifyTime(), item.getAccessTime(), item.getCreationTime());
		} catch (final IOException e) {
			throw new CloudsyncException("Can't set create, modify and access time of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}

		if (!nopermissions) {

			final UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
			final String groupName = item.getGroup();
			if (groupName != null) {
				try {
					final GroupPrincipal group = lookupService.lookupPrincipalByGroupName(groupName);
					Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setGroup(group);
				} catch (final UserPrincipalNotFoundException e) {
					if (!LocalFilesystemConnector.group_state.containsKey(item.getGroup())) {
						LocalFilesystemConnector.group_state.put(item.getGroup(), true);
						LOGGER.log(Level.WARNING, "group with name '" + item.getGroup() + "' not exists");
					}
					throw new CloudsyncException("Group '" + item.getGroup() + "' on '" + item.getPath() + "' not found");
				} catch (final IOException e) {
					throw new CloudsyncException("Can't set group '" + item.getGroup() + "' of '" + item.getPath() + "'", e);
				}
			}

			final String userName = item.getUser();
			if (userName != null) {
				try {
					final UserPrincipal user = lookupService.lookupPrincipalByName(userName);
					Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setOwner(user);
				} catch (final UserPrincipalNotFoundException e) {
					if (!LocalFilesystemConnector.user_state.containsKey(item.getUser())) {
						LocalFilesystemConnector.user_state.put(item.getUser(), true);
						LOGGER.log(Level.WARNING, "user with name '" + item.getUser() + "' not exists");
					}
					throw new CloudsyncException("User '" + item.getUser() + "' on '" + item.getPath() + "' not found");
				} catch (final IOException e) {
					throw new CloudsyncException("Can't set user '" + item.getUser() + "' of '" + item.getPath() + "'", e);
				}
			}

			final Integer permissions = item.getPermissions();
			if (permissions != null) {

				try {

					Files.setPosixFilePermissions(path, toPermissions(permissions));
				} catch (final IOException e) {
					throw new CloudsyncException("Can't set permissions of " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			}
		}
	}

	public List<Item> readFolder(final Structure structure, final Item item, final LinkType followlinks) throws CloudsyncException {

		final String currentPath = localPath + (StringUtils.isEmpty(item.getPath()) ? "" : Item.SEPARATOR + item.getPath());

		// System.out.println(currentPath);

		final List<Item> child_items = new ArrayList<Item>();

		final File folder = new File(currentPath);

		for (final File _file : folder.listFiles()) {

			Path path = _file.toPath();

			try {

				ItemType type = ItemType.UNKNOWN;
				if (Files.isSymbolicLink(path)) {

					String target;
					try {
						target = Files.readSymbolicLink(path).toString();
						final String firstChar = target.substring(0, 1);
						if (!firstChar.equals(Item.SEPARATOR)) {
							if (!firstChar.equals(".")) {
								target = "." + Item.SEPARATOR + target;
							}
							target = path.toString() + Item.SEPARATOR + target;
						}
						target = Paths.get(target).toFile().getCanonicalPath();
					} catch (final IOException e) {
						throw new CloudsyncException("Can't read '" + item.getTypeName() + "' on '" + currentPath + "'", e);
					}

					if (!followlinks.equals(LinkType.NONE) && followlinks.equals(LinkType.EXTERNAL) && !target.startsWith(localPath)) {

						final Path targetPath = Paths.get(target);
						if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
							path = targetPath;
						}
					}
				}

				final PosixFileAttributes attr = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				final Long filesize = attr.size();
				final FileTime creationTime = attr.creationTime();
				final FileTime modifyTime = attr.lastModifiedTime();
				final FileTime accessTime = attr.lastAccessTime();
				final String group = attr.group().getName();
				final String user = attr.owner().getName();
				final Integer permissions = fromPermissions(attr.permissions());

				if (attr.isDirectory()) {
					type = ItemType.FOLDER;
				} else if (attr.isRegularFile()) {
					type = ItemType.FILE;
				} else if (attr.isSymbolicLink()) {
					type = ItemType.LINK;
				} else {
					type = ItemType.UNKNOWN;
				}

				child_items.add(new Item(_file.getName(), null, type, filesize, creationTime, modifyTime, accessTime, group, user, permissions));
			} catch (final IOException e) {

				throw new CloudsyncException("Can't read attributes of '" + path.toString() + "'", e);
			}
		}

		return child_items;
	}

	private boolean exists(final Path path) {

		return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
	}

	private boolean isDir(final Path path) {

		return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
	}

	private Integer fromPermissions(final Set<PosixFilePermission> posixPerms) {

		int result = 0;
		for (final PosixFilePermission posixPerm : posixPerms) {

			result += fromPermMapping.get(posixPerm);
		}
		return result;
	}

	private Set<PosixFilePermission> toPermissions(final Integer perm) {

		final int mode = perm.intValue();
		final Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
		for (final int mask : toPermMapping.keySet()) {
			if (mask == (mode & mask)) {
				permissions.add(toPermMapping.get(mask));
			}
		}
		return permissions;
	}

	public File getFile(final Item item) {

		return new File(localPath + Item.SEPARATOR + item.getPath());
	}
}
