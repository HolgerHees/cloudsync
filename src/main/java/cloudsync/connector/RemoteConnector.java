package cloudsync.connector;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Handler;
import cloudsync.model.Item;
import cloudsync.model.RemoteItem;

public interface RemoteConnector
{
	public void init(String backupName, CmdOptions options) throws CloudsyncException;

	public List<RemoteItem> readFolder(Handler handler, Item parentItem) throws CloudsyncException;

	public void upload(Handler handler, Item item) throws CloudsyncException, NoSuchFileException;

	public void update(Handler handler, Item item, boolean with_filedata) throws CloudsyncException, NoSuchFileException;

	public void remove(Handler handler, Item item) throws CloudsyncException;

	public InputStream get(Handler handler, Item item) throws CloudsyncException;

	public void cleanHistory(Handler handler) throws CloudsyncException;
}
