package cloudsync.connector;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.helper.CloudsyncException;
import cloudsync.helper.CryptException;
import cloudsync.helper.Helper;
import cloudsync.helper.Structure;
import cloudsync.model.Item;
import cloudsync.model.ItemType;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.Drive.Files.Update;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Property;

public class RemoteGoogleDriveConnector implements RemoteConnector {

	private final static Logger LOGGER = Logger.getLogger(RemoteGoogleDriveConnector.class.getName());

	final static String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";
	final static String FOLDER = "application/vnd.google-apps.folder";
	final static String FILE = "application/octet-stream";

	final static int RETRY_COUNT = 2;

	private GoogleTokenResponse clientToken;
	private GoogleCredential credential;
	private Drive service;

	private final Path clientTokenPath;

	private final Map<String, File> cacheFiles;
	private final Map<String, File> cacheParents;

	private final String basePath;
	private final String backupName;
	private final String historyName;

	public RemoteGoogleDriveConnector(final String clientId, final String clientSecret, final String clientTokenPath, String basePath, final String backupName, final String historyName)
			throws CloudsyncException {

		cacheFiles = new HashMap<String, File>();
		cacheParents = new HashMap<String, File>();

		basePath = Helper.trim(basePath, Item.SEPARATOR);

		this.basePath = basePath;
		this.backupName = backupName;
		this.historyName = historyName;

		final HttpTransport httpTransport = new NetHttpTransport();
		final JsonFactory jsonFactory = new JacksonFactory();

		final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientId, clientSecret, Arrays.asList(DriveScopes.DRIVE)).setAccessType("offline")
				.setApprovalPrompt("auto").build();

		this.clientTokenPath = Paths.get(clientTokenPath);

		try {
			final String clientTokenAsJson = Files.exists(this.clientTokenPath) ? FileUtils.readFileToString(this.clientTokenPath.toFile()) : null;

			credential = new GoogleCredential.Builder().setTransport(new NetHttpTransport()).setJsonFactory(new GsonFactory()).setClientSecrets(clientId, clientSecret).build();

			if (StringUtils.isEmpty(clientTokenAsJson)) {

				final String url = flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URL).build();
				System.out.println("Please open the following URL in your browser then type the authorization code:");
				System.out.println("  " + url);
				final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				final String code = br.readLine();

				clientToken = flow.newTokenRequest(code).setRedirectUri(REDIRECT_URL).execute();

				credential.setFromTokenResponse(clientToken);

				storeClientToken(jsonFactory);
				LOGGER.log(Level.INFO, "\nclient token stored in '" + this.clientTokenPath + "'\n");
			} else {

				final JsonParser parser = jsonFactory.createJsonParser(clientTokenAsJson);
				clientToken = parser.parse(GoogleTokenResponse.class);
				credential.setFromTokenResponse(clientToken);
			}
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
	public void upload(final Structure structure, final Item item) throws CloudsyncException {
		upload(structure, item, 0);
	}

	private void upload(final Structure structure, final Item item, int count) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		try {
			final File parentDriveItem = _getDriveItem(item.getParent());
			final ParentReference parentReference = new ParentReference();
			parentReference.setId(parentDriveItem.getId());
			File driveItem = new File();
			driveItem.setTitle(structure.encryptText(item.getName()));
			driveItem.setParents(Arrays.asList(parentReference));
			final byte[] data = _prepareDriveItem(driveItem, item, structure, true);
			if (data == null) {
				driveItem = service.files().insert(driveItem).execute();
			} else {
				final InputStreamContent params = new InputStreamContent(FILE, new ByteArrayInputStream(data));
				params.setLength(data.length);
				Insert inserter = service.files().insert(driveItem, params);
				MediaHttpUploader uploader = inserter.getMediaHttpUploader();
				prepareUploader(uploader, data);
				driveItem = inserter.execute();
			}
			if (driveItem == null) {
				throw new CloudsyncException("Could not create item '" + item.getPath() + "'");
			}
			_addToCache(driveItem, null);
			item.setRemoteIdentifier(driveItem.getId());
		} catch (final CryptException e) {

			throw new CloudsyncException("Can't encrypt " + item.getTypeName() + " title of '" + item.getPath() + "'", e);
		} catch (final IOException e) {

			count = validateException("remote upload", item, e, count);

			upload(structure, item, count);
		}
	}

	@Override
	public void update(final Structure structure, final Item item, final boolean with_filedata) throws CloudsyncException {
		update(structure, item, with_filedata, 0);
	}

	public void update(final Structure structure, final Item item, final boolean with_filedata, int count) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		try {
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
			final byte[] data = _prepareDriveItem(driveItem, item, structure, with_filedata);
			if (data == null) {
				driveItem = service.files().update(item.getRemoteIdentifier(), driveItem).execute();
			} else {
				final InputStreamContent params = new InputStreamContent(FILE, new ByteArrayInputStream(data));
				params.setLength(data.length);
				Update updater = service.files().update(item.getRemoteIdentifier(), driveItem, params);
				MediaHttpUploader uploader = updater.getMediaHttpUploader();
				prepareUploader(uploader, data);
				driveItem = updater.execute();
			}
			if (driveItem == null) {
				throw new CloudsyncException("Could not update item '" + item.getPath() + "'");
			} else if (driveItem.getLabels().getTrashed()) {
				throw new CloudsyncException("Remote item '" + item.getPath() + "' [" + driveItem.getId() + "] is trashed\ntry to run with --nocache");
			}
			_addToCache(driveItem, null);
		} catch (final IOException e) {
			count = validateException("remote update", item, e, count);
			update(structure, item, with_filedata, count);
		}
	}

	@Override
	public void remove(final Structure structure, final Item item) throws CloudsyncException {
		remove(structure, item, 0);
	}

	public void remove(final Structure structure, final Item item, int count) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		try {
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

		} catch (final IOException e) {
			count = validateException("remote remove", item, e, count);
			remove(structure, item, count);
		}
	}

	@Override
	public InputStream get(final Structure structure, final Item item) throws CloudsyncException {
		return get(structure, item, 0);
	}

	public InputStream get(final Structure structure, final Item item, int count) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		try {
			final File driveItem = _getDriveItem(item);
			final String downloadUrl = driveItem.getDownloadUrl();
			final HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(downloadUrl)).execute();
			return resp.getContent();
		} catch (final IOException e) {
			count = validateException("remote get", item, e, count);
			return get(structure, item, count);
		}
	}

	@Override
	public List<Item> readFolder(final Structure structure, final Item parentItem) throws CloudsyncException {
		return readFolder(structure, parentItem, 0);
	}

	public List<Item> readFolder(final Structure structure, final Item parentItem, int count) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		try {
			final List<Item> child_items = new ArrayList<Item>();
			final List<File> childDriveItems = _readFolder(parentItem.getRemoteIdentifier());
			for (final File child : childDriveItems) {
				child_items.add(_prepareBackupItem(child, structure));
			}
			return child_items;
		} catch (final CryptException e) {
			throw new CloudsyncException("Can't decrypt child c", e);
		} catch (final IOException e) {
			count = validateException("remote fetch", parentItem, e, count);
			return readFolder(structure, parentItem, count);
		}
	}

	@Override
	public void cleanHistory(final Structure structure, final int history) throws CloudsyncException {

		validateCredential(structure.getRootItem());

		final File backupDriveFolder = _getBackupFolder();
		final File parentDriveItem = _getDriveFolder(basePath);

		try {
			final List<File> child_items = _readFolder(parentDriveItem.getId());

			Collections.sort(child_items, new Comparator<File>() {

				@Override
				public int compare(final File o1, final File o2) {

					final long v1 = o1.getCreatedDate().getValue();
					final long v2 = o2.getCreatedDate().getValue();

					if (v1 < v2) {
						return 1;
					}
					if (v1 > v2) {
						return -1;
					}
					return 0;
				}
			});

			int count = 0;
			for (int i = 0; i < child_items.size(); i++) {

				File childDriveFolder = child_items.get(i);

				if (backupDriveFolder.getId().equals(childDriveFolder.getId()) || !childDriveFolder.getTitle().startsWith(backupDriveFolder.getTitle())) {
					continue;
				}

				if (count < history) {
					count++;
				} else {
					LOGGER.log(Level.FINE, "cleanup history folder '" + childDriveFolder.getTitle() + "'");
					service.files().delete(child_items.get(i).getId()).execute();
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
		final List<File> result = request.execute().getItems();
		for (final File file : result) {
			child_items.add(file);
		}
		return child_items;
	}

	private byte[] _prepareDriveItem(final File driveItem, final Item item, final Structure structure, final boolean with_filedata) throws CloudsyncException {

		try {
			final String metadata = structure.encryptText(StringUtils.join(item.getMetadata(), ":"));

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
			driveItem.setProperties(properties);

			driveItem.setMimeType(item.isType(ItemType.FOLDER) ? FOLDER : FILE);

			byte[] data = null;
			if (with_filedata) {

				data = structure.getLocalEncryptedBinary(item);
			}
			return data;
		} catch (final CryptException e) {

			throw new CloudsyncException("Can't encrypt " + item.getTypeName() + " '" + item.getPath() + "'", e);
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected error on " + item.getTypeName() + " prepare", e);
		}
	}

	private Item _prepareBackupItem(final File driveItem, final Structure structure) throws CryptException, IOException {

		final List<String> parts = new ArrayList<String>();

		String[] metadata = null;
		final List<Property> properties = driveItem.getProperties();
		if (properties != null) {
			for (final Property property : driveItem.getProperties()) {

				final String key = property.getKey();
				if (!key.startsWith("metadata")) {
					continue;
				}

				parts.add(Integer.parseInt(key.substring(8)), property.getValue());
			}

			metadata = structure.decryptText(StringUtils.join(parts.toArray())).split(":", -1);
		}

		return Item.fromMetadata(structure.decryptText(driveItem.getTitle()), driveItem.getId(), driveItem.getMimeType().equals(FOLDER), metadata);
	}

	private File _getDriveItem(final Item item) throws CloudsyncException {

		final String id = item.getRemoteIdentifier();

		if (cacheFiles.containsKey(id)) {

			return cacheFiles.get(id);
		}

		File driveItem;

		try {
			driveItem = service.files().get(id).execute();
		} catch (final IOException e) {
			throw new CloudsyncException("Couldn't find remote item '" + item.getPath() + "' [" + id + "]\ntry to run with --nocache");
		}

		if (driveItem.getLabels().getTrashed()) {
			throw new CloudsyncException("Remote item '" + item.getPath() + "' [" + id + "] is trashed\ntry to run with --nocache");
		}

		_addToCache(driveItem, null);
		return driveItem;
	}

	private File _getHistoryFolder(final Item item) throws CloudsyncException {

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

		return _getDriveFolder(basePath + Item.SEPARATOR + historyName + Item.SEPARATOR + StringUtils.join(parentDriveTitles, Item.SEPARATOR));
	}

	private File _getBackupFolder() throws CloudsyncException {

		return _getDriveFolder(basePath + Item.SEPARATOR + backupName);
	}

	private File _getDriveFolder(final String path) throws CloudsyncException {

		try {
			File parentItem = service.files().get("root").execute();

			final String[] folderNames = StringUtils.split(path, Item.SEPARATOR);

			for (final String name : folderNames) {

				if (cacheParents.containsKey(parentItem.getId() + ':' + name)) {

					parentItem = cacheParents.get(parentItem.getId() + ':' + name);
				} else {

					final String q = "title='" + name + "' and '" + parentItem.getId() + "' in parents and trashed = false";

					final Drive.Files.List request = service.files().list();
					request.setQ(q);
					final List<File> result = request.execute().getItems();

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

	private int validateException(String name, Item item, IOException e, int count) throws CloudsyncException {

		if (count < RETRY_COUNT) {
			// Caused by: java.io.IOException: insufficient data written
			// VALIDATE: owncloud/Fotos/2008.10.04-09 Zypern/img_2875.jpg

			count++;

			LOGGER.log(Level.WARNING, "'" + e.getMessage() + "'. " + count + ". retry");

			return count;
		}

		throw new CloudsyncException("Unexpected error during " + name + " of " + item.getTypeName() + " '" + item.getPath() + "'", e);
	}

	private void validateCredential(final Item rootItem) throws CloudsyncException {

		if (service == null) {
			final HttpTransport httpTransport = new NetHttpTransport();
			final JsonFactory jsonFactory = new JacksonFactory();
			service = new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName("Backup").build();
			refreshCredential();
			rootItem.setRemoteIdentifier(_getBackupFolder().getId());
		} else if (credential.getExpiresInSeconds() < 600) {
			refreshCredential();
		}

		// LOGGER.log(Level.FINEST, " token expires in " +
		// credential.getExpiresInSeconds() + " seconds");
	}

	private void refreshCredential() throws CloudsyncException {

		try {
			if (credential.refreshToken()) {
				clientToken.setAccessToken(credential.getAccessToken());
				clientToken.setExpiresInSeconds(credential.getExpiresInSeconds());
				clientToken.setRefreshToken(credential.getRefreshToken());

				final JsonFactory jsonFactory = new JacksonFactory();
				storeClientToken(jsonFactory);
				LOGGER.log(Level.INFO, "refreshed client token stored in '" + clientTokenPath + "'");
			}
		} catch (IOException e) {
			throw new CloudsyncException("Can't refresh google client token", e);
		}
	}

	private void prepareUploader(MediaHttpUploader uploader, byte[] data) {

		int chunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE * 4;
		int chunkCount = (int) Math.ceil(data.length / (double) chunkSize);

		if (chunkCount > 1) {
			uploader.setDirectUploadEnabled(false);
			uploader.setChunkSize(chunkSize);
			uploader.setProgressListener(new RemoteGoogleDriveProgress(data.length));
		} else {

			uploader.setDirectUploadEnabled(true);
		}
	}

	private class RemoteGoogleDriveProgress implements MediaHttpUploaderProgressListener {

		int length;
		private DecimalFormat df;

		public RemoteGoogleDriveProgress(int length) {
			this.length = length;
			df = new DecimalFormat("00");
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
				double percent = mediaHttpUploader.getProgress() * 100;
				LOGGER.log(Level.FINEST, "\r  " + df.format(Math.ceil(percent)) + "% (" + convertToKB(mediaHttpUploader.getNumBytesUploaded()) + " of " + convertToKB(length) + " kb)", true);
				break;
			case MEDIA_COMPLETE:
				// System.out.println("Upload is complete!");
			default:
				break;
			}
		}

		private long convertToKB(long size) {

			return (long) Math.ceil(size / 1024);
		}
	}
}
