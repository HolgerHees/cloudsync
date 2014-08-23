package cloudsync.connector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntry.Builder;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.Helper;
import cloudsync.helper.Structure;
import cloudsync.model.DuplicateType;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.LinkType;
import cloudsync.model.PermissionType;

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

	private static Map<String, Boolean> principal_state = new HashMap<String, Boolean>();

	private final String localPath;

	public LocalFilesystemConnector(final String path) {

		if (path.startsWith(Item.SEPARATOR)) {
			localPath = Item.SEPARATOR + Helper.trim(path, Item.SEPARATOR);
		} else {
			localPath = Helper.trim(path, Item.SEPARATOR);
		}

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
			while (exists(Paths.get(path + "." + i))) {
				i++;
			}

			path += "." + i;

			item.setName(FilenameUtils.getName(path));
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

	public void upload(final Structure structure, final Item item, final DuplicateType duplicateFlag, final PermissionType permissionType) throws CloudsyncException {

		final String _path = localPath + Item.SEPARATOR + item.getPath();

		final Path path = Paths.get(_path);

		if (exists(path)) {

			if (!duplicateFlag.equals(DuplicateType.UPDATE)) {
				throw new CloudsyncException("Item '" + item.getPath() + "' already exists. Try to specify another '--duplicate' behavior.");
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

				try {

					final byte[] data = structure.getRemoteDecryptedBinary(item);
					final String link = new String(data);
					Files.createSymbolicLink(path, Paths.get(link));

				} catch (final IOException e) {

					throw new CloudsyncException("Unexpected error during local update of " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			} else if (item.isType(ItemType.FILE)) {

				try {
					final byte[] data = structure.getRemoteDecryptedBinary(item);
					if (!createChecksum(data).equals(item.getChecksum())) {
						throw new CloudsyncException("restored filechecksum differs from the original filechecksum");
					}
					if (item.getFilesize() != data.length) {
						throw new CloudsyncException("restored filesize differs from the original filesize");
					}
					final FileOutputStream fos = new FileOutputStream(path.toFile());
					try {
						fos.write(data);
					} finally {
						fos.close();
					}
				} catch (final IOException e) {

					throw new CloudsyncException("Unexpected error during local update of " + item.getTypeName() + " '" + item.getPath() + "'", e);
				}
			} else {
				throw new CloudsyncException("Unsupported type " + item.getTypeName() + "' on '" + item.getPath() + "'");
			}
		}

		try {
			if (item.isType(ItemType.LINK)) {
				// Files.setLastModifiedTime(path, item.getModifyTime());
			} else {
				Files.getFileAttributeView(path, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setTimes(item.getModifyTime(), item.getAccessTime(), item.getCreationTime());
			}
		} catch (final IOException e) {
			throw new CloudsyncException("Can't set create, modify and access time of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}

		if (permissionType.equals(PermissionType.SET) || permissionType.equals(PermissionType.TRY)) {

			final UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();

			Map<String, String[]> attributes = item.getAttributes();
			for (String type : attributes.keySet()) {

				GroupPrincipal group;
				UserPrincipal principal;

				try {
					String[] values = attributes.get(type);

					switch (type) {
					case Item.ATTRIBUTE_POSIX:
						PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
						if (posixView != null) {
							group = lookupService.lookupPrincipalByGroupName(values[0]);
							posixView.setGroup(group);
							principal = lookupService.lookupPrincipalByName(values[1]);
							posixView.setOwner(principal);
							if (values.length > 2)
								posixView.setPermissions(toPermissions(Integer.parseInt(values[2])));
						} else {
							String msg = "Can't restore 'posix' permissions on '" + item.getPath() + "'. They are not supported.";
							if (permissionType.equals(PermissionType.TRY))
								LOGGER.log(Level.WARNING, msg);
							else
								throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
						}
						break;
					case Item.ATTRIBUTE_DOS:
						DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
						if (dosView != null) {
							dosView.setArchive(Boolean.parseBoolean(values[0]));
							dosView.setHidden(Boolean.parseBoolean(values[1]));
							dosView.setReadOnly(Boolean.parseBoolean(values[2]));
							dosView.setSystem(Boolean.parseBoolean(values[3]));
						} else {
							String msg = "Can't restore 'dos' permissions on '" + item.getPath() + "'. They are not supported.";
							if (permissionType.equals(PermissionType.TRY))
								LOGGER.log(Level.WARNING, msg);
							else
								throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
						}
						break;
					case Item.ATTRIBUTE_ACL:
						AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
						if (aclView != null) {
							List<AclEntry> acls = aclView.getAcl();
							for (int i = 0; i < values.length; i = i + 4) {

								Builder aclEntryBuilder = AclEntry.newBuilder();

								aclEntryBuilder.setType(AclEntryType.valueOf(values[i]));
								aclEntryBuilder.setPrincipal(lookupService.lookupPrincipalByName(values[i + 1]));

								Set<AclEntryFlag> flags = new HashSet<AclEntryFlag>();
								for (String flag : StringUtils.splitPreserveAllTokens(values[i + 2], ",")) {
									flags.add(AclEntryFlag.valueOf(flag));
								}
								if (flags.size() > 0)
									aclEntryBuilder.setFlags(flags);

								Set<AclEntryPermission> aclPermissions = new HashSet<AclEntryPermission>();
								for (String flag : StringUtils.splitPreserveAllTokens(values[i + 3], ",")) {
									aclPermissions.add(AclEntryPermission.valueOf(flag));
								}
								if (aclPermissions.size() > 0)
									aclEntryBuilder.setPermissions(aclPermissions);
								acls.add(aclEntryBuilder.build());
							}
							aclView.setAcl(acls);
						} else {
							String msg = "Can't restore 'acl' permissions on '" + item.getPath() + "'. They are not supported.";
							if (permissionType.equals(PermissionType.TRY))
								LOGGER.log(Level.WARNING, msg);
							else
								throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
						}
						break;
					case Item.ATTRIBUTE_OWNER:
						FileOwnerAttributeView ownerView = Files.getFileAttributeView(path, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
						if (ownerView != null) {
							principal = lookupService.lookupPrincipalByName(values[0]);
							ownerView.setOwner(principal);
						} else {
							String msg = "Can't restore 'owner' permissions on '" + item.getPath() + "'. They are not supported.";
							if (permissionType.equals(PermissionType.TRY))
								LOGGER.log(Level.WARNING, msg);
							else
								throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
						}
						break;
					}
				} catch (final UserPrincipalNotFoundException e) {
					if (!LocalFilesystemConnector.principal_state.containsKey(e.getName())) {
						LocalFilesystemConnector.principal_state.put(e.getName(), true);
						LOGGER.log(Level.WARNING, "principal with name '" + e.getName() + "' not exists");
					}
					String msg = "Principal '" + e.getName() + "' on '" + item.getPath() + "' not found.";
					if (permissionType.equals(PermissionType.TRY))
						LOGGER.log(Level.WARNING, msg);
					else
						throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
				} catch (final IOException e) {
					String msg = "Can't set permissions of '" + item.getPath() + "'.";
					if (permissionType.equals(PermissionType.TRY))
						LOGGER.log(Level.WARNING, msg);
					else
						throw new CloudsyncException(msg + "\n  try to run with '--permissions try'");
				}
			}
		}
	}

	public File[] readFolder(final Item item) {

		final String currentPath = localPath + (StringUtils.isEmpty(item.getPath()) ? "" : Item.SEPARATOR + item.getPath());

		// System.out.println(currentPath);

		final File folder = new File(currentPath);

		if (!Files.exists(folder.toPath(), LinkOption.NOFOLLOW_LINKS)) {

			LOGGER.log(Level.WARNING, "skip '" + currentPath + "'. does not exists anymore.");
			return new File[] {};
		}

		return folder.listFiles();
	}

	public Item getItem(File file, final LinkType followlinks) throws CloudsyncException, NoSuchFileException {

		try {

			Path path = file.toPath();

			ItemType type = ItemType.UNKNOWN;

			if (Files.isSymbolicLink(path)) {

				String target;
				target = Files.readSymbolicLink(path).toString();
				final String firstChar = target.substring(0, 1);
				if (!firstChar.equals(Item.SEPARATOR)) {
					if (!firstChar.equals(".")) {
						target = "." + Item.SEPARATOR + target;
					}
					target = path.toString() + Item.SEPARATOR + target;
				}
				target = Paths.get(target).toFile().getCanonicalPath();

				if (!followlinks.equals(LinkType.NONE) && followlinks.equals(LinkType.EXTERNAL) && !target.startsWith(localPath)) {

					final Path targetPath = Paths.get(target);
					if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
						path = targetPath;
					}
				}
			}

			BasicFileAttributes basic_attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			final Long filesize = basic_attr.size();
			final FileTime creationTime = basic_attr.creationTime();
			final FileTime modifyTime = basic_attr.lastModifiedTime();
			final FileTime accessTime = basic_attr.lastAccessTime();

			if (basic_attr.isDirectory()) {
				type = ItemType.FOLDER;
			} else if (basic_attr.isRegularFile()) {
				type = ItemType.FILE;
			} else if (basic_attr.isSymbolicLink()) {
				type = ItemType.LINK;
			} else {
				type = ItemType.UNKNOWN;
			}

			Map<String, String[]> attributes = new HashMap<String, String[]>();

			PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (posixView != null) {
				final PosixFileAttributes attr = posixView.readAttributes();
				if (type.equals(ItemType.LINK)) {
					attributes.put(Item.ATTRIBUTE_POSIX, new String[] { attr.group().getName(), attr.owner().getName() });
				} else {
					attributes.put(Item.ATTRIBUTE_POSIX, new String[] { attr.group().getName(), attr.owner().getName(), fromPermissions(attr.permissions()).toString() });
				}
			} else {

				DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
				if (dosView != null) {
					final DosFileAttributes attr = dosView.readAttributes();
					attributes.put(Item.ATTRIBUTE_DOS, new String[] { attr.isArchive() ? "1" : "0", attr.isHidden() ? "1" : "0", attr.isReadOnly() ? "1" : "0", attr.isSystem() ? "1" : "0" });
				}
			}

			if (!type.equals(ItemType.LINK)) {

				AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
				if (aclView != null) {
					if (!attributes.containsKey(Item.ATTRIBUTE_POSIX))
						attributes.put(Item.ATTRIBUTE_OWNER, new String[] { aclView.getOwner().getName() });

					AclFileAttributeView parentAclView = Files.getFileAttributeView(path.getParent(), AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

					List<AclEntry> aclList = getLocalAclEntries(type, parentAclView.getAcl(), aclView.getAcl());
					if (aclList.size() > 0) {
						List<String> aclData = new ArrayList<String>();
						for (AclEntry acl : aclList) {
							List<String> flags = new ArrayList<String>();
							for (AclEntryFlag flag : acl.flags()) {
								flags.add(flag.name());
							}
							List<String> permissions = new ArrayList<String>();
							for (AclEntryPermission permission : acl.permissions()) {
								permissions.add(permission.name());
							}

							aclData.add(acl.type().name());
							aclData.add(acl.principal().getName());
							aclData.add(StringUtils.join(flags, ","));
							aclData.add(StringUtils.join(permissions, ","));
						}
						String[] arr = new String[aclData.size()];
						arr = aclData.toArray(arr);
						attributes.put(Item.ATTRIBUTE_ACL, arr);
					}
				} else if (!attributes.containsKey(Item.ATTRIBUTE_POSIX)) {

					FileOwnerAttributeView ownerView = Files.getFileAttributeView(path, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
					if (ownerView != null) {
						attributes.put(Item.ATTRIBUTE_OWNER, new String[] { ownerView.getOwner().getName() });
					}
				}
			}

			return Item.fromLocalData(file.getName(), type, filesize, creationTime, modifyTime, accessTime, attributes);

		} catch (final NoSuchFileException e) {

			throw e;

		} catch (final IOException e) {

			throw new CloudsyncException("Can't read attributes of '" + file.getAbsolutePath() + "'", e);
		}
	}

	private List<AclEntry> getLocalAclEntries(ItemType type, List<AclEntry> parentAclList, List<AclEntry> childAclList) {

		List<AclEntry> aclList = new ArrayList<AclEntry>();

		for (AclEntry childEntry : childAclList) {

			boolean found = false;
			for (AclEntry parentEntry : parentAclList) {

				if (!parentEntry.type().equals(childEntry.type()))
					continue;
				if (!parentEntry.principal().equals(childEntry.principal()))
					continue;
				if (!parentEntry.permissions().equals(childEntry.permissions()))
					continue;
				if (!parentEntry.flags().equals(childEntry.flags())) {
					if (parentEntry.flags().contains(AclEntryFlag.INHERIT_ONLY)) {
						found = true;
						break;
					} else {
						if (type.equals(ItemType.FOLDER)) {
							if (parentEntry.flags().contains(AclEntryFlag.DIRECTORY_INHERIT)) {
								found = true;
								break;
							}
						} else {
							if (parentEntry.flags().contains(AclEntryFlag.FILE_INHERIT)) {
								found = true;
								break;
							}
						}
					}
					continue;
				}
				found = true;
				break;
			}

			if (found)
				continue;

			// System.out.println("CHILD: "+childEntry.toString());
			/*
			 * System.out.println("\n\n");
			 * System.out.println("CHILD: "+childEntry.toString());
			 * 
			 * for(AclEntry parentEntry : parentAclList){
			 * 
			 * System.out.println("PARENT: "+parentEntry.toString()); }
			 * 
			 * System.out.println("\n\n");
			 */

			aclList.add(childEntry);
		}
		return aclList;
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

	public String createChecksum(final byte[] data) {

		return DigestUtils.md5Hex(data);
	}

	public byte[] getFileBinary(final Item item) throws CloudsyncException {

		File file = new File(localPath + Item.SEPARATOR + item.getPath());

		try {
			if (item.isType(ItemType.LINK)) {

				return Files.readSymbolicLink(file.toPath()).toString().getBytes();
			} else if (item.isType(ItemType.FILE)) {

				byte[] data = Files.readAllBytes(file.toPath());
				item.setChecksum(createChecksum(data));
				return data;
			}
			return null;
		} catch (final IOException e) {

			throw new CloudsyncException("Can't read data of '" + file.getAbsolutePath() + "'", e);
		}
	}
}
