package cloudsync.model.options;

public enum ExistingType
{
	STOP("stop"), UPDATE("update"), SKIP("skip"), RENAME("rename");

	private String	name;

	ExistingType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static ExistingType fromStringIgnoreCase(final String name)
	{
		if(name == null)
		{
			return null;
		}

		for (final ExistingType type : ExistingType.values())
		{
			if(!type.name.equals(name.toLowerCase()))
			{
				continue;
			}

			return type;
		}

		return null;
	}
}
