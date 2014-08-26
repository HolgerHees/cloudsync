package cloudsync.connector;

import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Helper;

public class RemoteDropboxOptions {

	private String appKey = null;
	private String appSecret = null;
	private String tokenPath = null;
	private String basePath = null;

	public RemoteDropboxOptions(CmdOptions options, String name) throws CloudsyncException {

		final String[] propertyNames = new String[] { "DROPBOX_APP_KEY", "DROPBOX_APP_SECRET", "DROPBOX_TOKEN_PATH", "DROPBOX_DIR" };
		for (final String propertyName : propertyNames) {
			if (StringUtils.isEmpty(options.getProperty(propertyName))) {
				throw new CloudsyncException("'" + propertyName + "' is not configured");
			}
		}

		appKey = options.getProperty("DROPBOX_APP_KEY");
		appSecret = options.getProperty("DROPBOX_APP_SECRET");
		tokenPath = Helper.preparePath(options.getProperty("DROPBOX_TOKEN_PATH"), name);
		basePath = options.getProperty("DROPBOX_DIR");
	}

	public String getAppKey() {
		return appKey;
	}

	public String getAppSecret() {
		return appSecret;
	}

	public String getTokenPath() {
		return tokenPath;
	}

	public String getBasePath() {
		return basePath;
	}
}
