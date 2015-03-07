package cloudsync.model;

public enum SyncType
{
	BACKUP("backup", true), RESTORE("restore", false), LIST("list", false), CLEAN("clean", true);

	private String	name;
	private boolean	checkpid;

	private SyncType(final String name, final boolean checkpid)
	{
		this.name = name;
		this.checkpid = checkpid;
	}

	public String getName()
	{
		return name;
	}

	public boolean checkPID()
	{
		return checkpid;
	}

	public static SyncType fromName(final String name)
	{
		for (final SyncType type : SyncType.values())
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
