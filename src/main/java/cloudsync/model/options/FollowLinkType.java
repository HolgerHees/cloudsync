package cloudsync.model.options;

public enum FollowLinkType
{
	EXTERNAL("external"), NONE("none"), ALL("all");

	private final String	name;

	FollowLinkType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static FollowLinkType fromStringIgnoreCase(final String name)
	{
		if(name == null)
		{
			return null;
		}

		for (final FollowLinkType type : FollowLinkType.values())
		{
			if (!type.name.equals(name.toLowerCase()))
			{
				continue;
			}

			return type;
		}

		return null;
	}
}
