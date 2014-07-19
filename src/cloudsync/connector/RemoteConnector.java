package cloudsync.connector;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.Structure;
import cloudsync.model.Item;
import cloudsync.model.RemoteItem;

public interface RemoteConnector {

	public List<RemoteItem> readFolder(Structure structure, Item parentItem) throws CloudsyncException;

	public void upload(Structure structure, Item item) throws CloudsyncException, NoSuchFileException;

	public void update(Structure structure, Item item, boolean with_filedata) throws CloudsyncException, NoSuchFileException;

	public void remove(Structure structure, Item item) throws CloudsyncException;

	public InputStream get(Structure structure, Item item) throws CloudsyncException;

	public void cleanHistory(Structure structure) throws CloudsyncException;
}
