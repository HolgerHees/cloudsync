package cloudsync.connector;

import java.io.InputStream;
import java.util.List;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.FileIOException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Handler;
import cloudsync.model.Item;
import cloudsync.model.RemoteItem;

public interface RemoteConnector
{
	void init(String backupName, CmdOptions options) throws CloudsyncException;

	List<RemoteItem> readFolder(Handler handler, Item parentItem) throws CloudsyncException;

	void upload(Handler handler, Item item) throws CloudsyncException, FileIOException;

	void update(Handler handler, Item item, boolean with_filedata) throws CloudsyncException, FileIOException;

	void remove(Handler handler, Item item) throws CloudsyncException;

	InputStream get(Handler handler, Item item) throws CloudsyncException;

	void cleanHistory(Handler handler) throws CloudsyncException;
}
