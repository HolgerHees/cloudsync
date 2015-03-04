package cloudsync.connector;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Handler;
import cloudsync.helper.Helper;
import cloudsync.model.StreamData;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.RemoteItem;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxEntry.File;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;

public class RemoteDropboxConnector implements RemoteConnector {

	private final static Logger LOGGER = Logger.getLogger(RemoteDropboxConnector.class.getName());

	public final static String SEPARATOR = "/";
	public final static String METADATA_SUFFIX = ".md";

	final static int MIN_SEARCH_BREAK = 5000;
	final static int MIN_SEARCH_RETRIES = 12;
	final static int MIN_RETRY_BREAK = 10000;
	final static int RETRY_COUNT = 6; // => readtimeout of 6 x 20 sec, 2 min

	private String backupRootPath;
	private String backupHistoryPath;

	private Map<String, DbxEntry> cacheFiles;

	private Path tokenPath;
	private String basePath;
	private String backupName;
	private Integer historyCount;

	private long lastValidate = 0;

	private DbxClient client;

	private boolean isInitialized;

	public RemoteDropboxConnector() {
	}

	@Override
	public void init(String backupName, CmdOptions options) throws CloudsyncException {

		RemoteDropboxOptions dropboxOptions = new RemoteDropboxOptions(options, backupName);
		Integer history = options.getHistory();

		cacheFiles = new HashMap<String, DbxEntry>();

		this.basePath = Helper.trim(dropboxOptions.getBasePath(), SEPARATOR);
		this.backupName = backupName;
		this.historyCount = history;
		this.tokenPath = Paths.get(dropboxOptions.getTokenPath());

		this.backupRootPath = basePath + SEPARATOR + backupName;
		this.backupHistoryPath = history > 0 ? this.backupRootPath + " " + new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new Date()) : null;

		try {

			String token = Files.exists(this.tokenPath) ? FileUtils.readFileToString(this.tokenPath.toFile()) : null;

			DbxAppInfo appInfo = new DbxAppInfo(dropboxOptions.getAppKey(), dropboxOptions.getAppSecret());

			DbxRequestConfig config = new DbxRequestConfig("Cloudsync/1.0", Locale.getDefault().toString());
			DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);

			if (StringUtils.isEmpty(token)) {

				final String url = webAuth.start();
				System.out.println("Please open the following URL in your browser, click \"Allow\" (you might have to log in first) and copy the authorization code and enter below");
				System.out.println("\n" + url + "\n");
				final String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

				DbxAuthFinish authFinish = webAuth.finish(code);
				token = authFinish.accessToken;

				FileUtils.write(this.tokenPath.toFile(), token);

				LOGGER.log(Level.INFO, "client token stored in '" + this.tokenPath + "'");
			}

			client = new DbxClient(config, token);

		} catch (DbxException e) {
			throw new CloudsyncException("Can't init remote dropbox connector", e);
		} catch (IOException e) {
			throw new CloudsyncException("Can't init remote dropbox connector", e);
		}
	}

	@Override
	public void upload(final Handler handler, final Item item) throws CloudsyncException, NoSuchFileException {

		initService(handler);

		int retryCount = 0;
		do {
			try {

				Item parentItem = item.getParent();
				String parentPath = buildPath(parentItem);
				String path = parentPath + SEPARATOR + handler.getLocalEncryptedTitle(item);
				DbxEntry entry;

				if (item.isType(ItemType.FOLDER)) {
					entry = client.createFolder(path);
				} else {
					StreamData data = handler.getLocalEncryptedBinary(item);
					if (data == null)
						data = new StreamData( new ByteArrayInputStream("".getBytes()), 0);
					entry = client.uploadFile(path, DbxWriteMode.add(), data.getLength(), data.getStream());
				}

				String metadata = handler.getLocalEncryptMetadata(item);

				client.uploadFile(path + METADATA_SUFFIX, DbxWriteMode.add(), metadata.length(), new ByteArrayInputStream(metadata.getBytes("ASCII")));
				_addToCache(entry);
				item.setRemoteIdentifier(entry.name);
				return;
			} catch (final IOException e) {
				retryCount = validateException("remote upload", item, e, retryCount);
				// TODO search for interrupted uploads
			} catch (final DbxException e) {
				retryCount = validateException("remote upload", item, e, retryCount);
				// TODO search for interrupted uploads
			}
		} while (true);
	}

	@Override
	public void update(final Handler handler, final Item item, final boolean with_filedata) throws CloudsyncException, NoSuchFileException {

		initService(handler);

		int retryCount = 0;
		do {
			try {

				String path = buildPath(item);
				if (with_filedata) {
					StreamData data = handler.getLocalEncryptedBinary(item);
					if (data != null) {
						client.uploadFile(path, DbxWriteMode.force(), data.getLength(), data.getStream());
					}
				}
				String metadata = handler.getLocalEncryptMetadata(item);
				client.uploadFile(path + METADATA_SUFFIX, DbxWriteMode.force(), metadata.length(), new ByteArrayInputStream(metadata.getBytes("ASCII")));
				return;
			} catch (final IOException e) {
				retryCount = validateException("remote update", item, e, retryCount);
			} catch (final DbxException e) {
				retryCount = validateException("remote update", item, e, retryCount);
			}
		} while (true);
	}

	@Override
	public void remove(final Handler handler, final Item item) throws CloudsyncException {

		initService(handler);

		int retryCount = 0;
		do {
			try {
				String path = buildPath(item);
				if (backupHistoryPath != null) {

					client.move(path, path.replaceFirst(backupRootPath, backupHistoryPath));
					client.move(path, path.replaceFirst(backupRootPath + METADATA_SUFFIX, backupHistoryPath + METADATA_SUFFIX));
				} else {
					client.delete(path);
				}
				_removeFromCache(path);
				return;
			} catch (final DbxException e) {
				retryCount = validateException("remote remove", item, e, retryCount);
			}
		} while (true);
	}

	@Override
	public InputStream get(final Handler handler, final Item item) throws CloudsyncException {

		initService(handler);

		int retryCount = 0;
		do {
			try {
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				try {
					client.getFile(buildPath(item), null, outputStream);
					return new ByteArrayInputStream(outputStream.toByteArray());
				} finally {
					outputStream.close();
				}
			} catch (final DbxException e) {
				retryCount = validateException("fetch metadata", null, e, retryCount);
			} catch (final IOException e) {
				retryCount = validateException("fetch metadata", null, e, retryCount);
			}
		} while (true);
	}

	@Override
	public List<RemoteItem> readFolder(final Handler handler, final Item parentItem) throws CloudsyncException {

		initService(handler);

		int retryCount = 0;
		do {
			try {
				// refreshCredential();

				final List<RemoteItem> child_items = new ArrayList<RemoteItem>();
				Map<String, DbxEntry[]> childContainer = new HashMap<String, DbxEntry[]>();
				DbxEntry.WithChildren listing = client.getMetadataWithChildren(buildPath(parentItem));
				for (DbxEntry child : listing.children) {
					String[] nameParts = child.name.split("\\.");

					DbxEntry[] entries = childContainer.get(nameParts[0]);
					if (entries == null)
						entries = new DbxEntry[2];
					if (nameParts.length == 2)
						entries[1] = child;
					else
						entries[0] = child;

					childContainer.put(nameParts[0], entries);

				}

				for (final DbxEntry[] childData : childContainer.values()) {
					child_items.add(_prepareBackupItem(childData, handler));
				}
				return child_items;
			} catch (final DbxException e) {
				retryCount = validateException("remote fetch", parentItem, e, retryCount);
			}
		} while (true);
	}

	@Override
	public void cleanHistory(final Handler handler) throws CloudsyncException {

		initService(handler);
		try {

			final List<DbxEntry> child_items = new ArrayList<DbxEntry>();
			for (DbxEntry entry : client.getMetadataWithChildren(basePath).children) {

				if (!entry.name.startsWith(backupName) || entry.name.equals(backupName))
					continue;

				child_items.add(entry);
			}

			if (child_items.size() > historyCount) {
				Collections.sort(child_items, new Comparator<DbxEntry>() {

					@Override
					public int compare(final DbxEntry o1, final DbxEntry o2) {

						return o1.name.compareTo(o2.name);
					}
				});

				for (DbxEntry entry : child_items.subList(historyCount, child_items.size())) {

					LOGGER.log(Level.FINE, "cleanup history folder '" + entry.name + "'");
					client.delete(entry.path);
				}
			}
		} catch (final DbxException e) {

			throw new CloudsyncException("Unexpected error during history cleanup", e);
		}
	}

	private RemoteItem _prepareBackupItem(final DbxEntry[] childData, final Handler handler) throws CloudsyncException {

		String encryptedMetadata = null;
		if (childData[1] != null) {
			int retryCount = 0;
			do {
				try {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					try {
						client.getFile(childData[1].path, null, outputStream);
						encryptedMetadata = outputStream.toString("ASCII");
						break;
					} finally {
						outputStream.close();
					}
				} catch (final DbxException e) {
					retryCount = validateException("fetch metadata", null, e, retryCount);
				} catch (final IOException e) {
					retryCount = validateException("fetch metadata", null, e, retryCount);
				}
			} while (true);
		}

		Long size;
		long time;

		if (childData[0].isFile()) {
			File file = childData[0].asFile();
			size = file.numBytes;
			time = file.lastModified.getTime();
		} else {

			size = 0l;
			time = 0l;
		}
		
		String title = handler.getDecryptedText( childData[0].name );
		String metadata = handler.getDecryptedText( encryptedMetadata );

		return handler.getRemoteItem(childData[0].name, childData[0].isFolder(), title, metadata, size, FileTime.fromMillis(time));
	}

	private void _removeFromCache(final String path) {

		cacheFiles.remove(path);
	}

	private void _addToCache(final DbxEntry entry) {

		if (entry.isFolder()) {
			cacheFiles.put(entry.path, entry);
		}
	}

	private void sleep(long duration) {

		try {
			Thread.sleep(duration);
		} catch (InterruptedException ex) {
		}
	}

	private int validateException(String name, Item item, Exception e, int count) throws CloudsyncException {

		if (count < RETRY_COUNT) {
			long currentValidate = System.currentTimeMillis();
			long current_retry_break = (currentValidate - lastValidate);
			if (lastValidate > 0 && current_retry_break < MIN_RETRY_BREAK) {
				sleep(MIN_RETRY_BREAK - current_retry_break);
			}

			lastValidate = currentValidate;

			count++;

			LOGGER.log(Level.WARNING, getExceptionMessage(e) + name + " - retry " + count + "/" + RETRY_COUNT);

			return count;
		}

		throw new CloudsyncException("Unexpected error during " + name + (item == null ? "" : " of " + item.getTypeName() + " '" + item.getPath() + "'"), e);
	}

	private String getExceptionMessage(Exception e) {

		String msg = e.getMessage();
		if (msg.contains("\n"))
			msg = msg.split("\n")[0];
		return "ioexception: '" + msg + "' - ";
	}

	private void initService(Handler handler) throws CloudsyncException {

		if (isInitialized)
			return;

		handler.getRootItem().setRemoteIdentifier(this.backupName);
		isInitialized = true;
	}

	private String buildPath(Item item) {

		List<String> names = new ArrayList<String>();

		do {
			names.add(item.getRemoteIdentifier());

		} while ((item = item.getParent()) != null);

		Collections.reverse(names);

		return backupRootPath + SEPARATOR + StringUtils.join(names, SEPARATOR);
	}
}
