package cloudsync.connector;

import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Helper;

public class RemoteGoogleDriveOptions
{
	private String	clientID						= null;
	private String	clientSecret					= null;
	private String	clientTokenPath					= null;
	private String	clientBasePath 					= null;
	private String	serviceAccountEmail				= null;
	private String	serviceAccountUser				= null;
	private String	serviceAccountPrivateKeyP12Path	= null;
	
	public RemoteGoogleDriveOptions(CmdOptions options, String name) throws CloudsyncException
	{
		final String[] propertyNames = new String[] {
				"GOOGLE_DRIVE_CLIENT_ID", 
				"GOOGLE_DRIVE_CLIENT_SECRET", 
				"GOOGLE_DRIVE_CLIENT_TOKEN_PATH",
				"GOOGLE_DRIVE_DIR",
				"GOOGLE_DRIVE_SERVICE_ACCOUNT_EMAIL",
				"GOOGLE_DRIVE_SERVICE_ACCOUNT_USER",
				"GOOGLE_DRIVE_SERVICE_ACCOUNT_PRIVATE_KEY_P12_PATH"};

		for (final String propertyName : propertyNames)
		{
			if (StringUtils.isEmpty(options.getProperty(propertyName)))
			{
				throw new CloudsyncException("'" + propertyName + "' is not configured");
			}
		}

		clientID = options.getProperty("GOOGLE_DRIVE_CLIENT_ID");
		clientSecret = options.getProperty("GOOGLE_DRIVE_CLIENT_SECRET");
		clientTokenPath = Helper.preparePath(options.getProperty("GOOGLE_DRIVE_CLIENT_TOKEN_PATH"), name);
		clientBasePath = options.getProperty("GOOGLE_DRIVE_DIR");
		serviceAccountEmail = options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_EMAIL");
		serviceAccountUser = options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_USER");
		serviceAccountPrivateKeyP12Path = Helper.preparePath(options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_PRIVATE_KEY_P12_PATH"));
	}

	public String getClientID()
	{
		return clientID;
	}

	public String getClientSecret()
	{
		return clientSecret;
	}

	public String getClientTokenPath()
	{
		return clientTokenPath;
	}

	public String getClientBasePath()
	{
		return clientBasePath;
	}

	public String getServiceAccountEmail() {
		return serviceAccountEmail;
	}

	public String getServiceAccountUser() {
		return serviceAccountUser;
	}

	public String getServiceAccountPrivateKeyP12Path() {
		return serviceAccountPrivateKeyP12Path;
	}
}
