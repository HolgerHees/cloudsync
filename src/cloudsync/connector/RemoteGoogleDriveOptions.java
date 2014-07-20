package cloudsync.connector;

import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Helper;

public class RemoteGoogleDriveOptions {

	private String clientID = null;
	private String clientSecret = null;
	private String clientTokenPath = null;
	private String clientBasePath = null;

	public RemoteGoogleDriveOptions(CmdOptions options, String name) throws CloudsyncException {

		clientID = options.getProperty("REMOTE_CLIENT_ID");
		clientSecret = options.getProperty("REMOTE_CLIENT_SECRET");
		clientTokenPath = Helper.preparePath(options.getProperty("REMOTE_CLIENT_TOKEN_PATH"), name);
		clientBasePath = options.getProperty("REMOTE_DIR");

		final String[] propertyNames = new String[] { "REMOTE_CLIENT_ID", "REMOTE_CLIENT_SECRET", "REMOTE_CLIENT_TOKEN_PATH" };
		for (final String propertyName : propertyNames) {
			if (StringUtils.isEmpty(options.getProperty(propertyName))) {
				throw new CloudsyncException("'" + propertyName + "' is not configured");
			}
		}
	}

	public String getClientID() {
		return clientID;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public String getClientTokenPath() {
		return clientTokenPath;
	}

	public String getClientBasePath() {
		return clientBasePath;
	}
}
