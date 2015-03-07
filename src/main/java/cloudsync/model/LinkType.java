package cloudsync.model;

public enum LinkType
{
	EXTERNAL("external"), NONE("none"), ALL("all");

	private String	name;

	private LinkType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static LinkType fromName(final String name)
	{
		for (final LinkType type : LinkType.values())
		{
			if (!type.name.equals(name))
			{
				continue;
			}

			return type;
		}

		return null;
	}
}
