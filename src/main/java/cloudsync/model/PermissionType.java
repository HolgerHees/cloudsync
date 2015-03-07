package cloudsync.model;

public enum PermissionType
{
	SET("set"), IGNORE("ignore"), TRY("try");

	private String	name;

	private PermissionType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static PermissionType fromName(final String name)
	{
		for (final PermissionType type : PermissionType.values())
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
