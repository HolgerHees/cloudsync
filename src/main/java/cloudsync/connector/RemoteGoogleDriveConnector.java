package cloudsync.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.Drive.Files.Update;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Property;

public class RemoteGoogleDriveConnector implements RemoteConnector {

	private final static Logger LOGGER = Logger.getLogger(RemoteGoogleDriveConnector.class.getName());

	public final static String SEPARATOR = "/";

	final static String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";
	final static String FOLDER = "application/vnd.google-apps.folder";
	final static String FILE = "application/octet-stream";

	final static int MIN_SEARCH_BREAK = 5000;
	final static int MIN_SEARCH_RETRIES = 12;
	final static int MIN_RETRY_BREAK = 10000;
	final static int RETRY_COUNT = 6; // => readtimeout of 6 x 20 sec, 2 min
	final static int CHUNK_COUNT = 4; // * 256kb
	final static int MAX_RESULTS = 1000;
	final static long MIN_TOKEN_REFRESH_TIMEOUT = 600;

	private GoogleTokenResponse clientToken;
	private GoogleCredential credential;
	private Drive service;

	private Path clientTokenPath;

	private Map<String, File> cacheFiles;
	private Map<String, File> cacheParents;

	private String basePath;
	private String backupName;
	private String historyName;
	private Integer historyCount;
	private long lastValidate = 0;

	public RemoteGoogleDriveConnector() {
	}

	@Override
	public void init(String backupName, CmdOptions options) throws CloudsyncException {

		RemoteGoogleDriveOptions googleDriveOptions = new RemoteGoogleDriveOptions(options, backupName);
		Integer history = options.getHistory();

		cacheFiles = new HashMap<String, File>();
		cacheParents = new HashMap<String, File>();

		this.basePath = Helper.trim(googleDriveOptions.getClientBasePath(), SEPARATOR);
		this.backupName = backupName;
		this.historyCount = history;
		this.historyName = history > 0 ? backupName + " " + new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new Date()) : null;

		final HttpTransport httpTransport = new NetHttpTransport();
		final JsonFactory jsonFactory = new JacksonFactory();

		final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, googleDriveOptions.getClientID(), googleDriveOptions.getClientSecret(),
				Arrays.asList(DriveScopes.DRIVE)).setAccessType("offline").setApprovalPrompt("auto").build();

		this.clientTokenPath = Paths.get(googleDriveOptions.getClientTokenPath());

		try {
			final String clientTokenAsJson = Files.exists(this.clientTokenPath) ? FileUtils.readFileToString(this.clientTokenPath.toFile()) : null;

			credential = new GoogleCredential.Builder().setTransport(new NetHttpTransport()).setJsonFactory(new GsonFactory())
					.setClientSecrets(googleDriveOptions.getClientID(), googleDriveOptions.getClientSecret()).build();

			if (StringUtils.isEmpty(clientTokenAsJson)) {

				final String url = flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URL).build();
				System.out.println("Please open the following URL in your browser, copy the authorization code and enter below.");
				System.out.println("\n" + url + "\n");
				final String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

				clientToken = flow.newTokenRequest(code).setRedirectUri(REDIRECT_URL).execute();

				storeClientToken(jsonFactory);
				LOGGER.log(Level.INFO, "client token stored in '" + this.clientTokenPath + "'");
			} else {

				clientToken = jsonFactory.createJsonParser(clientTokenAsJson).parse(GoogleTokenResponse.class);
			}

			credential.setFromTokenResponse(clientToken);

		} catch (final IOException e) {
			throw new CloudsyncException("Can't init remote google drive connector", e);
		}
	}

	private void storeClientToken(final JsonFactory jsonFactory) throws IOException {

		final StringWriter jsonTrWriter = new StringWriter();
		final JsonGenerator generator = jsonFactory.createJsonGenerator(jsonTrWriter);
		generator.serialize(clientToken);
		generator.flush();
		generator.close();

		FileUtils.writeStringToFile(clientTokenPath.toFile(), jsonTrWriter.toString());
	}

	@Override
	public void upload(final Handler handler, final Item item) throws CloudsyncException, NoSuchFileException {

		initService(handler);

		String title = handler.getLocalEncryptedTitle(item);
		File parentDriveItem = null;
		File driveItem;
		int retryCount = 0;
		do {
			try {
				refreshCredential();
				parentDriveItem = _getDriveItem(item.getParent());
				final ParentReference parentReference = new ParentReference();
				parentReference.setId(parentDriveItem.getId());
				driveItem = new File();
				driveItem.setTitle(title);
				driveItem.setParents(Arrays.asList(parentReference));
				final StreamData data = _prepareDriveItem(driveItem, item, handler, true);
				if (data == null) {
					driveItem = service.files().insert(driveItem).execute();
				} else {
					final InputStreamContent params = new InputStreamContent(FILE, data.getStream());
					params.setLength(data.getLength());
					Insert inserter = service.files().insert(driveItem, params);
					MediaHttpUploader uploader = inserter.getMediaHttpUploader();
					prepareUploader(uploader, data.getLength());
					driveItem = inserter.execute();
				}
				if (driveItem == null) {
					throw new CloudsyncException("Could not create item '" + item.getPath() + "'");
				}
				_addToCache(driveItem, null);
				item.setRemoteIdentifier(driveItem.getId());
				return;
			} catch (final NoSuchFileException e) {
				throw e;
			} catch (final IOException e) {
				if (parentDriveItem != null) {
					for (int i = 0; i < MIN_SEARCH_RETRIES; i++) {
						driveItem = _searchDriveItem(item.getParent(), title);
						if (driveItem != null) {

							LOGGER.log(Level.WARNING, getExceptionMessage(e) + "found uploaded item - try to update");

							item.setRemoteIdentifier(driveItem.getId());
							update(handler, item, true);
							return;
						}
						LOGGER.log(Level.WARNING, getExceptionMessage(e) + "item not uploaded - retry " + (i + 1) + "/" + MIN_SEARCH_RETRIES + " - wait " + MIN_SEARCH_BREAK + " ms");
						sleep(MIN_SEARCH_BREAK);
					}
				}
				retryCount = validateException("remote upload", item, e, retryCount);
			}
		} while (true);
	}

	@Override
	public void update(final Handler handler, final Item item, final boolean with_filedata) throws CloudsyncException, NoSuchFileException {

		initService(handler);

		int retryCount = 0;
		do {
			try {
				refreshCredential();

				if (item.isType(ItemType.FILE)) {

					final File _parentDriveItem = _getHistoryFolder(item);
					if (_parentDriveItem != null) {

						final File copyOfdriveItem = new File();
						final ParentReference _parentReference = new ParentReference();
						_parentReference.setId(_parentDriveItem.getId());
						copyOfdriveItem.setParents(Arrays.asList(_parentReference));
						// copyOfdriveItem.setTitle(driveItem.getTitle());
						// copyOfdriveItem.setMimeType(driveItem.getMimeType());
						// copyOfdriveItem.setProperties(driveItem.getProperties());
						final File _copyOfDriveItem = service.files().copy(item.getRemoteIdentifier(), copyOfdriveItem).execute();
						if (_copyOfDriveItem == null) {
							throw new CloudsyncException("Could not make a history snapshot of item '" + item.getPath() + "'");
						}
					}
				}
				File driveItem = new File();
				final StreamData data = _prepareDriveItem(driveItem, item, handler, with_filedata);
				if (data == null) {
					driveItem = service.files().update(item.getRemoteIdentifier(), driveItem).execute();
				} else {
					final InputStreamContent params = new InputStreamContent(FILE, data.getStream());
					params.setLength(data.getLength());
					Update updater = service.files().update(item.getRemoteIdentifier(), driveItem, params);
					MediaHttpUploader uploader = updater.getMediaHttpUploader();
					prepareUploader(uploader, data.getLength());
					driveItem = updater.execute();
				}
				if (driveItem == null) {
					throw new CloudsyncException("Could not update item '" + item.getPath() + "'");
				} else if (driveItem.getLabels().getTrashed()) {
					throw new CloudsyncException("Remote item '" + item.getPath() + "' [" + driveItem.getId() + "] is trashed\ntry to run with --nocache");
				}
				_addToCache(driveItem, null);
				return;
			} catch (final NoSuchFileException e) {
				throw e;
			} catch (final IOException e) {
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
				refreshCredential();

				final File _parentDriveItem = _getHistoryFolder(item);
				if (_parentDriveItem != null) {

					final ParentReference parentReference = new ParentReference();
					parentReference.setId(_parentDriveItem.getId());
					File driveItem = new File();
					driveItem.setParents(Arrays.asList(parentReference));
					driveItem = service.files().patch(item.getRemoteIdentifier(), driveItem).execute();
					if (driveItem == null) {
						throw new CloudsyncException("Could not make a history snapshot of item '" + item.getPath() + "'");
					}
				} else {
					service.files().delete(item.getRemoteIdentifier()).execute();
				}
				_removeFromCache(item.getRemoteIdentifier());
				return;
			} catch (final IOException e) {
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
				refreshCredential();

				final File driveItem = _getDriveItem(item);
				final String downloadUrl = driveItem.getDownloadUrl();
				final HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(downloadUrl)).execute();
				return resp.getContent();
			} catch (final IOException e) {
				retryCount = validateException("remote get", item, e, retryCount);
			}
		} while (true);
	}

	@Override
	public List<RemoteItem> readFolder(final Handler handler, final Item parentItem) throws CloudsyncException {

		initService(handler);

		int retryCount = 0;
		do {
			try {
				refreshCredential();

				final List<RemoteItem> child_items = new ArrayList<RemoteItem>();
				final List<File> childDriveItems = _readFolder(parentItem.getRemoteIdentifier());
				for (final File child : childDriveItems) {
					child_items.add(_prepareBackupItem(child, handler));
				}
				return child_items;
			} catch (final IOException e) {
				retryCount = validateException("remote fetch", parentItem, e, retryCount);
			}
		} while (true);
	}

	@Override
	public void cleanHistory(final Handler handler) throws CloudsyncException {

		initService(handler);

		final File backupDriveFolder = _getBackupFolder();
		final File parentDriveItem = _getDriveFolder(basePath);

		try {
			refreshCredential();

			final List<File> child_items = new ArrayList<File>();
			for (File file : _readFolder(parentDriveItem.getId())) {

				if (backupDriveFolder.getId().equals(file.getId()) || !file.getTitle().startsWith(backupDriveFolder.getTitle())) {
					continue;
				}
				child_items.add(file);
			}

			if (child_items.size() > historyCount) {
				Collections.sort(child_items, new Comparator<File>() {

					@Override
					public int compare(final File o1, final File o2) {

						final long v1 = o1.getCreatedDate().getValue();
						final long v2 = o2.getCreatedDate().getValue();

						if (v1 < v2)
							return 1;
						if (v1 > v2)
							return -1;
						return 0;
					}
				});

				for (File file : child_items.subList(historyCount, child_items.size())) {

					LOGGER.log(Level.FINE, "cleanup history folder '" + file.getTitle() + "'");
					service.files().delete(file.getId()).execute();
				}
			}
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected error during history cleanup", e);
		}
	}

	private List<File> _readFolder(final String id) throws IOException {

		final List<File> child_items = new ArrayList<File>();

		final String q = "'" + id + "' in parents and trashed = false";
		final Drive.Files.List request = service.files().list();
		request.setQ(q);
		request.setMaxResults(MAX_RESULTS);

		do {

			FileList files = request.execute();

			final List<File> result = files.getItems();
			for (final File file : result) {
				child_items.add(file);
			}
			request.setPageToken(files.getNextPageToken());

		} while (request.getPageToken() != null && request.getPageToken().length() > 0);

		return child_items;
	}

	private StreamData _prepareDriveItem(final File driveItem, final Item item, final Handler handler, final boolean with_filedata) throws CloudsyncException, NoSuchFileException {

		StreamData data = null;
		if (with_filedata) {

			// "getLocalEncryptedBinary" should be called before "getMetadata"
			// to generate the needed checksum
			data = handler.getLocalEncryptedBinary(item);
		}

		final String metadata = handler.getLocalEncryptMetadata(item);

		final List<Property> properties = new ArrayList<Property>();

		final int length = metadata.length();
		int partCounter = 0;
		// max 118 bytes (key+value)
		for (int i = 0; i < length; i += 100, partCounter++) {
			final String part = metadata.substring(i, Math.min(length, i + 100));
			final Property property = new Property();
			property.setKey("metadata" + partCounter);
			property.setValue(part);
			property.setVisibility("PRIVATE");
			properties.add(property);
		}
		
		final Property property = new Property();
		property.setKey("metadataParts");
		property.setValue(Integer.toString(partCounter));
		property.setVisibility("PRIVATE");
		properties.add(property);

		driveItem.setProperties(properties);
		
		driveItem.setMimeType(item.isType(ItemType.FOLDER) ? FOLDER : FILE);

		return data;
	}

	private RemoteItem _prepareBackupItem(final File driveItem, final Handler handler) throws CloudsyncException {

		final List<Property> properties = driveItem.getProperties();
		
		final Map<Integer,String> metadataMap = new HashMap<Integer, String>();
		int metadataPartCount = -1;

		if (properties != null) {
			for (final Property property : properties) {

				final String key = property.getKey();
				if (!key.startsWith("metadata")) {
					continue;
				}

				if (key.equals("metadataParts")) {
					metadataPartCount = Integer.parseInt(property.getValue());
				}
				else{
					metadataMap.put(Integer.parseInt(key.substring(8)), property.getValue());
				}
			}
		}
		
		if( metadataPartCount == -1 ) metadataPartCount = metadataMap.size();
		
		final List<String> parts = new ArrayList<String>();
		for( int i = 0; i < metadataPartCount; i++ ){
			parts.add(i, metadataMap.get(Integer.valueOf(i)));
		}
		
		return handler.getRemoteItem(driveItem.getId(), driveItem.getMimeType().equals(FOLDER), driveItem.getTitle(), StringUtils.join(parts.toArray()), driveItem.getFileSize(),
				FileTime.fromMillis(driveItem.getCreatedDate().getValue()));
	}

	private File _searchDriveItem(final Item parentItem, String title) throws CloudsyncException {

		int retryCount = 0;
		do {
			try {
				final String q = "title='" + title + "' and '" + parentItem.getRemoteIdentifier() + "' in parents and trashed = false";
				final Drive.Files.List request = service.files().list();
				request.setQ(q);
				final List<File> result = request.execute().getItems();
				return result.size() == 0 ? null : result.get(0);
			} catch (final IOException e) {
				retryCount = validateException("remote search", parentItem, e, retryCount);
			}
		} while (true);
	}

	private File _getDriveItem(final Item item) throws CloudsyncException, IOException {

		final String id = item.getRemoteIdentifier();

		if (cacheFiles.containsKey(id)) {

			return cacheFiles.get(id);
		}

		File driveItem;

		try {
			driveItem = service.files().get(id).execute();

		} catch (HttpResponseException e) {

			if (e.getStatusCode() == 404) {
				throw new CloudsyncException("Couldn't find remote item '" + item.getPath() + "' [" + id + "]\ntry to run with --nocache");
			}

			throw e;
		}

		if (driveItem.getLabels().getTrashed()) {
			throw new CloudsyncException("Remote item '" + item.getPath() + "' [" + id + "] is trashed\ntry to run with --nocache");
		}

		_addToCache(driveItem, null);
		return driveItem;
	}

	private File _getHistoryFolder(final Item item) throws CloudsyncException, IOException {

		if (historyName == null) {
			return null;
		}

		final File driveRoot = _getBackupFolder();
		final List<String> parentDriveTitles = new ArrayList<String>();
		Item parentItem = item;
		do {
			parentItem = parentItem.getParent();
			if (parentItem.getRemoteIdentifier().equals(driveRoot.getId())) {
				break;
			}
			final File parentDriveItem = _getDriveItem(parentItem);
			parentDriveTitles.add(0, parentDriveItem.getTitle());
		} while (true);

		return _getDriveFolder(basePath + SEPARATOR + historyName + SEPARATOR + StringUtils.join(parentDriveTitles, SEPARATOR));
	}

	private File _getBackupFolder() throws CloudsyncException {

		return _getDriveFolder(basePath + SEPARATOR + backupName);
	}

	private File _getDriveFolder(final String path) throws CloudsyncException {

		try {
			File parentItem = service.files().get("root").execute();

			final String[] folderNames = StringUtils.split(path, SEPARATOR);

			for (final String name : folderNames) {

				if (cacheParents.containsKey(parentItem.getId() + ':' + name)) {

					parentItem = cacheParents.get(parentItem.getId() + ':' + name);
				} else {

					final String q = "title='" + name + "' and '" + parentItem.getId() + "' in parents and trashed = false";

					final Drive.Files.List request = service.files().list();
					request.setQ(q);
					request.setMaxResults(MAX_RESULTS);

					do {

						FileList files = request.execute();

						final List<File> result = files.getItems();

						// array('q' => q))

						File _parentItem;

						if (result.size() == 0) {

							final File folder = new File();
							folder.setTitle(name);
							folder.setMimeType(FOLDER);
							final ParentReference parentReference = new ParentReference();
							parentReference.setId(parentItem.getId());
							folder.setParents(Arrays.asList(parentReference));
							_parentItem = service.files().insert(folder).execute();
							if (_parentItem == null) {
								throw new CloudsyncException("Could not create folder '" + name + "'");
							}
						} else if (result.size() == 1) {
							_parentItem = result.get(0);
						} else {

							throw new CloudsyncException("base path '" + path + "' not unique");
						}

						if (!_parentItem.getMimeType().equals(FOLDER)) {
							throw new CloudsyncException("No folder found at '" + path + "'");
						}

						_addToCache(_parentItem, parentItem);

						parentItem = _parentItem;

						request.setPageToken(files.getNextPageToken());
					} while (request.getPageToken() != null && request.getPageToken().length() > 0);
				}
			}
			return parentItem;
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected Exception", e);
		}
	}

	private void _removeFromCache(final String id) {

		cacheFiles.remove(id);
	}

	private void _addToCache(final File driveItem, final File parentDriveItem) {

		if (driveItem.getMimeType().equals(FOLDER)) {
			cacheFiles.put(driveItem.getId(), driveItem);
		}
		if (parentDriveItem != null) {
			cacheParents.put(parentDriveItem.getId() + ':' + driveItem.getTitle(), driveItem);
		}
	}

	private void sleep(long duration) {

		try {
			Thread.sleep(duration);
		} catch (InterruptedException ex) {
		}
	}

	private int validateException(String name, Item item, IOException e, int count) throws CloudsyncException {

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

		throw new CloudsyncException("Unexpected error during " + name + " of " + item.getTypeName() + " '" + item.getPath() + "'", e);
	}

	private String getExceptionMessage(IOException e) {

		String msg = e.getMessage();
		if (msg.contains("\n"))
			msg = msg.split("\n")[0];
		return "ioexception: '" + msg + "' - ";
	}

	public void initService(Handler handler) throws CloudsyncException {

		if (service != null)
			return;

		final HttpTransport httpTransport = new NetHttpTransport();
		final JsonFactory jsonFactory = new JacksonFactory();
		service = new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName("Backup").build();
		credential.setExpiresInSeconds(MIN_TOKEN_REFRESH_TIMEOUT);
		try {
			refreshCredential();
		} catch (IOException e) {
			throw new CloudsyncException("couldn't refresh google drive token");
		}
		handler.getRootItem().setRemoteIdentifier(_getBackupFolder().getId());
	}

	private void refreshCredential() throws IOException {

		if (credential.getExpiresInSeconds() > MIN_TOKEN_REFRESH_TIMEOUT)
			return;

		if (credential.refreshToken()) {
			clientToken.setAccessToken(credential.getAccessToken());
			clientToken.setExpiresInSeconds(credential.getExpiresInSeconds());
			clientToken.setRefreshToken(credential.getRefreshToken());

			final JsonFactory jsonFactory = new JacksonFactory();
			storeClientToken(jsonFactory);
			LOGGER.log(Level.INFO, "refreshed client token stored in '" + clientTokenPath + "'");
		}
	}

	private void prepareUploader(MediaHttpUploader uploader, long length) {

		int chunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE * CHUNK_COUNT;
		int chunkCount = (int) Math.ceil(length / (double) chunkSize);

		if (chunkCount > 1) {
			uploader.setDirectUploadEnabled(false);
			uploader.setChunkSize(chunkSize);
			uploader.setProgressListener(new RemoteGoogleDriveProgress(this, length));
		} else {

			uploader.setDirectUploadEnabled(true);
		}
	}

	private class RemoteGoogleDriveProgress implements MediaHttpUploaderProgressListener {

		long length;
		private DecimalFormat df;
		private long lastBytes;
		private long lastTime;
		private RemoteGoogleDriveConnector connector;

		public RemoteGoogleDriveProgress(RemoteGoogleDriveConnector connector, long length) {
			this.length = length;
			this.connector = connector;
			df = new DecimalFormat("00");
			lastBytes = 0;
			lastTime = System.currentTimeMillis();
		}

		@Override
		public void progressChanged(MediaHttpUploader mediaHttpUploader) throws IOException {
			if (mediaHttpUploader == null)
				return;

			switch (mediaHttpUploader.getUploadState()) {
			case INITIATION_COMPLETE:
				break;
			case INITIATION_STARTED:
			case MEDIA_IN_PROGRESS:

				this.connector.refreshCredential();

				double percent = mediaHttpUploader.getProgress() * 100;

				long currentTime = System.currentTimeMillis();

				String msg = "\r  " + df.format(Math.ceil(percent)) + "% (" + convertToKB(mediaHttpUploader.getNumBytesUploaded()) + " of " + convertToKB(length) + " kb)";

				if (mediaHttpUploader.getUploadState().equals(UploadState.MEDIA_IN_PROGRESS)) {

					long speed = convertToKB((mediaHttpUploader.getNumBytesUploaded() - lastBytes) / ((currentTime - lastTime) / 1000.0));
					msg += " - " + speed + " kb/s";
				}
				
				LOGGER.log(Level.FINEST, msg, true);

				lastTime = currentTime;
				lastBytes = mediaHttpUploader.getNumBytesUploaded();
				break;
			case MEDIA_COMPLETE:
				// System.out.println("Upload is complete!");
			default:
				break;
			}
		}

		private long convertToKB(double size) {

			return (long) Math.ceil(size / 1024);
		}
	}
}
