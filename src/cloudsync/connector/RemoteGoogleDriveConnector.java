package cloudsync.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Property;

public class RemoteGoogleDriveConnector implements RemoteConnector {

	private final static Logger LOGGER = Logger.getLogger(RemoteGoogleDriveConnector.class.getName());

	final static String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";
	final static String FOLDER = "application/vnd.google-apps.folder";
	final static String FILE = "application/octet-stream";

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

	private void _init(final Item rootItem) throws CloudsyncException {

		final HttpTransport httpTransport = new NetHttpTransport();
		final JsonFactory jsonFactory = new JacksonFactory();
		service = new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName("Backup").build();
		rootItem.setRemoteIdentifier(_getBackupFolder().getId());

		if (!credential.getAccessToken().equals(clientToken.getAccessToken())) {

			clientToken.setAccessToken(credential.getAccessToken());
			clientToken.setExpiresInSeconds(credential.getExpiresInSeconds());
			clientToken.setRefreshToken(credential.getRefreshToken());

			try {

				storeClientToken(jsonFactory);
				LOGGER.log(Level.INFO, "\nrefreshed client token stored in '" + clientTokenPath + "'\n");
			} catch (final IOException e) {
				throw new CloudsyncException("Can't refresh google client token file", e);
			}
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

		if (service == null) {
			_init(structure.getRootItem());
		}

		try {
			final File parentDriveItem = _getDriveItem(item.getParent());
			final ParentReference parentReference = new ParentReference();
			parentReference.setId(parentDriveItem.getId());
			File driveItem = new File();
			driveItem.setTitle(structure.encryptText(item.getName()));
			driveItem.setParents(Arrays.asList(parentReference));
			final InputStreamContent params = _prepareDriveItem(driveItem, item, structure, true);
			if (params == null) {
				driveItem = service.files().insert(driveItem).execute();
			} else {
				driveItem = service.files().insert(driveItem, params).execute();
			}
			if (driveItem == null) {
				throw new CloudsyncException("Could not create item '" + item.getPath() + "'");
			}
			_addToCache(driveItem, null);
			item.setRemoteIdentifier(driveItem.getId());
		} catch (final CryptException e) {

			throw new CloudsyncException("Can't encrypt " + item.getTypeName() + " title of '" + item.getPath() + "'", e);
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected error during remote upload of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}
	}

	@Override
	public void update(final Structure structure, final Item item, final boolean with_filedata) throws CloudsyncException {

		if (service == null) {
			_init(structure.getRootItem());
		}

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
			final InputStreamContent params = _prepareDriveItem(driveItem, item, structure, with_filedata);
			if (params == null) {
				driveItem = service.files().update(item.getRemoteIdentifier(), driveItem).execute();
			} else {
				driveItem = service.files().update(item.getRemoteIdentifier(), driveItem, params).execute();
			}
			if (driveItem == null) {
				throw new CloudsyncException("Could not update item '" + item.getPath() + "'");
			} else if (driveItem.getLabels().getTrashed()) {
				throw new CloudsyncException("Remote item '" + item.getPath() + "' [" + driveItem.getId() + "] is trashed\ntry to run with --nocache");
			}
			_addToCache(driveItem, null);
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected error during remote update of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}
	}

	@Override
	public void remove(final Structure structure, final Item item) throws CloudsyncException {

		if (service == null) {
			_init(structure.getRootItem());
		}

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
				service.files().delete(item.getRemoteIdentifier());
			}

			_removeFromCache(item.getRemoteIdentifier());

		} catch (final IOException e) {
			throw new CloudsyncException("Unexpected error during remote remove of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}
	}

	@Override
	public InputStream get(final Structure structure, final Item item) throws CloudsyncException {

		if (service == null) {
			_init(structure.getRootItem());
		}

		try {
			final File driveItem = _getDriveItem(item);
			final String downloadUrl = driveItem.getDownloadUrl();
			final HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(downloadUrl)).execute();
			return resp.getContent();
		} catch (final IOException e) {

			throw new CloudsyncException("Unexpected error during remote get of " + item.getTypeName() + " '" + item.getPath() + "'", e);
		}
	}

	@Override
	public List<Item> readFolder(final Structure structure, final Item parentItem) throws CloudsyncException {

		if (service == null) {
			_init(structure.getRootItem());
		}

		try {
			final List<Item> child_items = new ArrayList<Item>();
			final List<File> childDriveItems = _readFolder(parentItem.getRemoteIdentifier());
			for (final File child : childDriveItems) {
				child_items.add(_prepareBackupItem(child, structure));
			}
			return child_items;
		} catch (final IOException e) {
			throw new CloudsyncException("Unexpected error during remote fetch folder '" + parentItem.getPath() + "'", e);
		} catch (final CryptException e) {
			throw new CloudsyncException("Can't decrypt child c", e);
		}
	}

	@Override
	public void cleanHistory(final Structure structure, final int history) throws CloudsyncException {

		if (service == null) {
			_init(structure.getRootItem());
		}

		final File backupDriveFolder = _getBackupFolder();
		final File parentDriveItem = _getDriveFolder(basePath);

		try {
			final List<File> child_items = _readFolder(parentDriveItem.getId());

			Collections.sort(child_items, new Comparator<File>() {

				@Override
				public int compare(final File o1, final File o2) {

					final long v1 = o1.getModifiedDate().getValue();
					final long v2 = o2.getModifiedDate().getValue();

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

				if (backupDriveFolder.getId().equals(child_items.get(i).getId())) {
					continue;
				}

				if (count < history) {
					count++;
				} else {
					LOGGER.log(Level.FINE, "cleanup history folder '" + child_items.get(i).getTitle());
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

	private InputStreamContent _prepareDriveItem(final File driveItem, final Item item, final Structure structure, final boolean with_filedata) throws CloudsyncException {

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

			InputStreamContent params = null;
			if (with_filedata) {

				final InputStream data = structure.getLocalEncryptedBinaryStream(item);
				if (data != null) {
					params = new InputStreamContent(FILE, data);
				}
			}
			return params;
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
				
		if( driveItem.getMimeType().equals(FOLDER)){
			cacheFiles.put(driveItem.getId(), driveItem);
		}
		if (parentDriveItem != null) {
			cacheParents.put(parentDriveItem.getId() + ':' + driveItem.getTitle(), driveItem);
		}
	}
}
