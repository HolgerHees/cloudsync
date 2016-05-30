package cloudsync.model.options;

public enum NetworkErrorType
{
	EXCEPTION("exception"), ASK("ask"), CONTINUE("continue");

	private String name;

	NetworkErrorType(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public static NetworkErrorType fromStringIgnoreCase(final String name)
	{
		if(name == null)
		{
			return null;
		}

		for (final NetworkErrorType type : NetworkErrorType.values())
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