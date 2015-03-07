package cloudsync.connector;

import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Helper;

public class RemoteGoogleDriveOptions
{
	private String	clientID		= null;
	private String	clientSecret	= null;
	private String	clientTokenPath	= null;
	private String	clientBasePath	= null;

	public RemoteGoogleDriveOptions(CmdOptions options, String name) throws CloudsyncException
	{
		final String[] propertyNames = new String[] { "GOOGLE_DRIVE_CLIENT_ID", "GOOGLE_DRIVE_CLIENT_SECRET", "GOOGLE_DRIVE_CLIENT_TOKEN_PATH",
				"GOOGLE_DRIVE_DIR" };
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
}
