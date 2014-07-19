package cloudsync.model;

import java.nio.file.attribute.FileTime;

public class RemoteItem extends Item {

	private Long remoteFilesize;
	private FileTime remoteCreationtime;

	public RemoteItem(String name, String remoteIdentifier, ItemType type, Long filesize, FileTime creationtime, FileTime modifytime, FileTime accesstime, String group, String user,
			Integer permissions, Long remoteFilesize, FileTime remoteCreationtime) {

		super(name, remoteIdentifier, type, filesize, creationtime, modifytime, accesstime, group, user, permissions);

		this.remoteFilesize = remoteFilesize;
		this.remoteCreationtime = remoteCreationtime;
	}

	public Long getRemoteFilesize() {

		return remoteFilesize;
	}

	public FileTime getRemoteCreationTime() {

		return remoteCreationtime;
	}
}
