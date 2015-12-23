package cloudsync.model;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cloudsync.exceptions.FileIOException;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.helper.Handler;

public class Item
{
	public final static Integer	METADATA_VERSION	= 1;

	public final static String	SEPARATOR			= File.separator;

	public final static String	ATTRIBUTE_POSIX		= "posix";
	public final static String	ATTRIBUTE_DOS		= "dos";
	public final static String	ATTRIBUTE_OWNER		= "owner";
	public final static String	ATTRIBUTE_ACL		= "acl";

	private final static String	METADATA_SEPARATOR	= ":";
	private final static String	ATTRIBUTE_SEPARATOR	= "|";

	private Item				parent;

	protected String			name;
	protected String			remoteIdentifier;
	protected ItemType			type;
	protected Long				filesize;
	protected Long				creationtime;
	protected Long				modifytime;
	protected Long				accesstime;
	protected String[]			attributes;

	private String				checksum;

	protected boolean			needsMetadataUpgrade;

	protected Map<String, Item>	children;

	protected Item()
	{
	}

	public static Item getDummyRoot()
	{
		Item item = new Item();
		item.name = "";
		item.remoteIdentifier = "";
		item.type = ItemType.FOLDER;
		item.children = new HashMap<String, Item>();
		return item;
	}

	public static Item fromCSV(final CSVRecord values)
	{
		final String name = FilenameUtils.getName(values.get(0));
		final String remoteIndentifier = values.get(1);

		String[] metadata = new String[values.size() - 2];
		for (int i = 2; i < values.size(); i++)
		{
			metadata[i - 2] = values.get(i);
		}

		return initItem(new Item(), name, remoteIndentifier, metadata);
	}

	public String[] toCSVArray()
	{
		return ArrayUtils.addAll(new String[] { getPath(), remoteIdentifier }, getDataArray());
	}

	public static RemoteItem fromMetadata(final String remoteIdentifier, final boolean isFolder, final String name, final String metadata, Long remoteFilesize,
			FileTime remoteCreationtime)
	{
		RemoteItem item = new RemoteItem(remoteFilesize, remoteCreationtime);

		if (!StringUtils.isEmpty(metadata))
		{
			item = (RemoteItem) initItem(item, name, remoteIdentifier, metadata.split(METADATA_SEPARATOR));
		}
		else
		{
			item.name = name;
			item.remoteIdentifier = remoteIdentifier;
			item.type = isFolder ? ItemType.FOLDER : ItemType.FILE;
			if (item.type.equals(ItemType.FOLDER))
			{
				item.children = new HashMap<String, Item>();
			}
		}

		return item;
	}

	public String getMetadata(Handler handler) throws FileIOException
	{
		if (needsMetadataUpgrade)
		{
			if (checksum == null)
			{
				// force a checksum update
				handler.getLocalProcessedBinary(this);
			}
		}

		return StringUtils.join(getDataArray(), METADATA_SEPARATOR);
	}

	public static Item fromLocalData(String name, ItemType type, Long filesize, FileTime creationtime, FileTime modifytime, FileTime accesstime,
			Map<String, String[]> map)
	{
		Item item = new Item();
		item.name = name;
		item.type = type;
		item.filesize = filesize;
		item.creationtime = creationtime.to(TimeUnit.SECONDS);
		item.modifytime = modifytime.to(TimeUnit.SECONDS);
		item.accesstime = accesstime.to(TimeUnit.SECONDS);
		item.attributes = convertToAttributes(map);
		if (item.type.equals(ItemType.FOLDER))
		{
			item.children = new HashMap<String, Item>();
		}
		return item;
	}

	private static Item initItem(final Item item, final String name, final String remoteIdentifier, final String[] metadata)
	{
		int metadataVersion = 0;
		if (metadata.length != 9 || metadata[8].contains(ATTRIBUTE_SEPARATOR))
		{
			metadataVersion = Integer.parseInt(metadata[0]);
		}

		ItemType type = null;
		Long filesize = null;
		Long creationtime = null;
		Long modifytime = null;
		Long accesstime = null;
		String checksum = null;
		String[] attributes = null;

		switch ( metadataVersion )
		{
			case 0:
				type = ItemType.fromString(metadata[0]);
				filesize = StringUtils.isEmpty(metadata[1]) ? null : Long.parseLong(metadata[1]);
				creationtime = StringUtils.isEmpty(metadata[2]) ? null : Long.parseLong(metadata[2]);
				modifytime = StringUtils.isEmpty(metadata[3]) ? null : Long.parseLong(metadata[3]);
				accesstime = StringUtils.isEmpty(metadata[4]) ? null : Long.parseLong(metadata[4]);

				final String group = StringUtils.isEmpty(metadata[5]) ? null : metadata[5];
				final String user = StringUtils.isEmpty(metadata[6]) ? null : metadata[6];
				final Integer permissions = StringUtils.isEmpty(metadata[7]) ? null : Integer.parseInt(metadata[7]);
				Map<String, String[]> map = new HashMap<String, String[]>();
				if (permissions == null) map.put(ATTRIBUTE_POSIX, new String[] { group, user });
				else map.put(ATTRIBUTE_POSIX, new String[] { group, user, permissions.toString() });
				attributes = convertToAttributes(map);

				checksum = metadata[8];
				break;
			default:

				type = ItemType.fromString(metadata[1]);
				filesize = StringUtils.isEmpty(metadata[2]) ? null : Long.parseLong(metadata[2]);
				creationtime = StringUtils.isEmpty(metadata[3]) ? null : Long.parseLong(metadata[3]);
				modifytime = StringUtils.isEmpty(metadata[4]) ? null : Long.parseLong(metadata[4]);
				accesstime = StringUtils.isEmpty(metadata[5]) ? null : Long.parseLong(metadata[5]);
				checksum = metadata[6];
				attributes = ArrayUtils.subarray(metadata, 7, metadata.length);

				break;
		}

		item.name = name;
		item.remoteIdentifier = remoteIdentifier;
		item.type = type;
		item.filesize = filesize;
		item.creationtime = creationtime;
		item.modifytime = modifytime;
		item.accesstime = accesstime;
		item.attributes = attributes;
		if (item.type.equals(ItemType.FOLDER))
		{
			item.children = new HashMap<String, Item>();
		}
		item.checksum = checksum;
		item.needsMetadataUpgrade = metadataVersion != METADATA_VERSION;
		return item;
	}

	private String[] getDataArray()
	{
		return ArrayUtils.addAll(
				new String[] { METADATA_VERSION.toString(), type.toString(), filesize != null ? filesize.toString() : null,
						creationtime != null ? creationtime.toString() : null, modifytime != null ? modifytime.toString() : null,
						accesstime != null ? accesstime.toString() : null, checksum }, attributes);
	}

	private static Map<String, String[]> convertToMap(String[] attributes)
	{
		Map<String, String[]> map = new HashMap<String, String[]>();
		for (String row : attributes)
		{
			String[] values = StringUtils.splitPreserveAllTokens(row, ATTRIBUTE_SEPARATOR);
			map.put(values[0], ArrayUtils.subarray(values, 1, values.length));
		}
		return map;
	}

	private static String[] convertToAttributes(Map<String, String[]> map)
	{
		List<String> list = new ArrayList<String>();
		for (String type : map.keySet())
		{
			list.add(type + ATTRIBUTE_SEPARATOR + StringUtils.join(map.get(type), ATTRIBUTE_SEPARATOR));
		}
		String[] arr = new String[list.size()];
		arr = list.toArray(arr);
		return arr;
	}

	public void setParent(final Item parent)
	{
		this.parent = parent;
	}

	public Item getParent()
	{
		return parent;
	}

	public void setRemoteIdentifier(final String remoteIdentifier)
	{
		this.remoteIdentifier = remoteIdentifier;
	}

	public void addChild(final Item child)
	{
		children.put(child.name, child);
	}

	public void removeChild(final Item child)
	{
		children.remove(child.name);
	}

	public Item getChildByName(final String name)
	{
		return children.get(name);
	}

	public Map<String, Item> getChildren()
	{
		return new HashMap<String, Item>(children);
	}

	public String getTypeName()
	{
		return type.getName();
	}

	public ItemType getType()
	{
		return type;
	}

	public boolean isTypeChanged(final Item item)
	{
		return !type.equals(item.type);
	}

	public boolean isFiledataChanged(final Item item)
	{
		if (type.equals(ItemType.FILE) || item.equals(ItemType.LINK))
		{
			if (isChanged(filesize, item.filesize))
			{
				return true;
			}
			if (isChanged(creationtime, item.creationtime))
			{
				return true;
			}
			if (isChanged(modifytime, item.modifytime))
			{
				return true;
			}
			/*
			 * if (isChanged(accesstime, item.accesstime)) { return true; }
			 */
		}
		return false;
	}

	public boolean isMetadataChanged(final Item item)
	{
		if (isChanged(filesize, item.filesize))
		{
			return true;
		}
		if (isChanged(creationtime, item.creationtime))
		{
			return true;
		}
		if (isChanged(modifytime, item.modifytime))
		{
			return true;
		}
		/*
		 * if (isChanged(accesstime, item.accesstime)) { return true; }
		 */
		if ((attributes == null) != (item.attributes == null) || (attributes != null && !Arrays.equals(attributes, item.attributes)))
		{
			return true;
		}
		if (needsMetadataUpgrade)
		{
			return true;
		}
		return false;
	}

	public boolean isMetadataFormatChanged()
	{
		return needsMetadataUpgrade;
	}

	private boolean isChanged(final Object o1, final Object o2)
	{
		return (o1 == null) != (o2 == null) || (o1 != null && !o1.equals(o2));
	}

	public void update(final Item item)
	{
		type = item.type;
		filesize = item.filesize;
		modifytime = item.modifytime;
		creationtime = item.creationtime;
		attributes = item.attributes;
	}

	public String getPath()
	{
		String path = "";
		if (parent != null)
		{
			path += parent.getPath();
		}
		if (!path.isEmpty())
		{
			path += Item.SEPARATOR;
		}
		path += name;
		return path;
	}

	public String getName()
	{
		return name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public void setChecksum(final String checksum)
	{
		this.checksum = checksum;
	}

	public String getChecksum()
	{
		return this.checksum;
	}

	public String getRemoteIdentifier()
	{
		return remoteIdentifier;
	}

	public boolean isType(final ItemType type)
	{
		return this.type.equals(type);
	}

	public Map<String, String[]> getAttributes()
	{
		return convertToMap(attributes);
	}

	public Long getFilesize()
	{
		return filesize;
	}

	public FileTime getCreationTime()
	{

		return FileTime.from(creationtime.longValue(), TimeUnit.SECONDS);
	}

	public FileTime getModifyTime()
	{

		return FileTime.from(modifytime.longValue(), TimeUnit.SECONDS);
	}

	public FileTime getAccessTime()
	{

		return FileTime.from(accesstime.longValue(), TimeUnit.SECONDS);
	}

	public String getInfo()
	{
		StringBuffer info = new StringBuffer();
		info.append( getTypeName() );
		info.append( " '" );
		info.append( getPath() );
		info.append( "'" );
		return info.toString();
	}
}
