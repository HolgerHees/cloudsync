package cloudsync.model;

import java.nio.file.attribute.FileTime;

public class RemoteItem extends Item
{
	private Long		remoteFilesize;
	private FileTime	remoteCreationtime;

	public RemoteItem(Long remoteFilesize, FileTime remoteCreationtime)
	{
		super();

		this.remoteFilesize = remoteFilesize;
		this.remoteCreationtime = remoteCreationtime;
	}

	public Long getRemoteFilesize()
	{
		return remoteFilesize;
	}

	public FileTime getRemoteCreationTime()
	{
		return remoteCreationtime;
	}
}
