package cloudsync.model.options;

public enum SyncType
{
	BACKUP("backup", true), RESTORE("restore", false), LIST("list", false), CLEAN("clean", true);

	private String	name;
	private boolean	checkpid;

	SyncType(final String name, final boolean checkpid)
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
}
