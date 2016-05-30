package cloudsync.model.options;

public enum FileErrorType
{
	EXCEPTION("exception"), MESSAGE("message");

	private String name;

	FileErrorType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static FileErrorType fromStringIgnoreCase(final String name)
	{
		if(name == null)
		{
			return null;
		}

		for (final FileErrorType type : FileErrorType.values())
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