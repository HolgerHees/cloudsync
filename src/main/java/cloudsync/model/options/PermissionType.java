package cloudsync.model.options;

public enum PermissionType
{
	SET("set"), IGNORE("ignore"), TRY("try");

	private String	name;

	PermissionType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static PermissionType fromStringIgnoreCase(final String name)
	{
		if(name == null)
		{
			return null;
		}

		for (final PermissionType type : PermissionType.values())
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
