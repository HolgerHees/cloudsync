package cloudsync.connector;

import java.util.ArrayList;
import java.util.List;

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
		clientBasePath = options.getProperty("GOOGLE_DRIVE_DIR");
		if( StringUtils.isEmpty(clientBasePath) )
		{
			throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_DIR"));
		}

		clientID = options.getProperty("GOOGLE_DRIVE_CLIENT_ID");
		clientSecret = options.getProperty("GOOGLE_DRIVE_CLIENT_SECRET");
		clientTokenPath = Helper.preparePath(options.getProperty("GOOGLE_DRIVE_CLIENT_TOKEN_PATH"), name);

		serviceAccountEmail = options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_EMAIL");
		serviceAccountUser = options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_USER");
		serviceAccountPrivateKeyP12Path = Helper.preparePath(options.getProperty("GOOGLE_DRIVE_SERVICE_ACCOUNT_PRIVATE_KEY_P12_PATH"));

		boolean isClientTokenAccountInvalid = StringUtils.isEmpty(clientID) || StringUtils.isEmpty(clientSecret) || StringUtils.isEmpty(clientTokenPath);
		boolean isServiceAccountInvalid = StringUtils.isEmpty(serviceAccountEmail) || StringUtils.isEmpty(serviceAccountUser) ||  StringUtils.isEmpty(serviceAccountPrivateKeyP12Path);
				
		if( isClientTokenAccountInvalid && isServiceAccountInvalid )
		{
			if( !StringUtils.isEmpty(serviceAccountEmail) || !StringUtils.isEmpty(serviceAccountUser) || !StringUtils.isEmpty(serviceAccountPrivateKeyP12Path) )
			{
				if( StringUtils.isEmpty(serviceAccountEmail) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_SERVICE_ACCOUNT_EMAIL"));
				if( StringUtils.isEmpty(serviceAccountUser) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_SERVICE_ACCOUNT_USER"));
				if( StringUtils.isEmpty(serviceAccountPrivateKeyP12Path) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_SERVICE_ACCOUNT_PRIVATE_KEY_P12_PATH"));
			}
			else if( !StringUtils.isEmpty(clientID) || !StringUtils.isEmpty(clientSecret) || !StringUtils.isEmpty(clientTokenPath) )
			{
				if( StringUtils.isEmpty(clientID) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_CLIENT_ID"));
				if( StringUtils.isEmpty(clientSecret) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_CLIENT_SECRET"));
				if( StringUtils.isEmpty(clientTokenPath) ) throw new CloudsyncException(prepareMessage("GOOGLE_DRIVE_CLIENT_TOKEN_PATH"));
			}
			else
			{
				throw new CloudsyncException("You must configure either a 'google client token based account' or a 'google service account'");
			}
		}
	}
	
	private String prepareMessage(String name)
	{
		return "'"+name+"' is not configured";
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
