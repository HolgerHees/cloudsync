package cloudsync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import cloudsync.connector.LocalFilesystemConnector;
import cloudsync.connector.RemoteConnector;
import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.UsageException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Crypt;
import cloudsync.helper.Handler;
import cloudsync.logging.LogconsoleHandler;
import cloudsync.logging.LogfileFormatter;
import cloudsync.logging.LogfileHandler;
import cloudsync.model.SyncType;

public class Cloudsync
{
	private final static Logger	LOGGER	= Logger.getLogger(Cloudsync.class.getName());

	private final CmdOptions	options;

	public Cloudsync(final String[] args)
	{
		this.options = new CmdOptions(args);

		final Logger logger = Logger.getLogger("cloudsync");
		logger.setLevel(Level.ALL);
		final ConsoleHandler handler = new LogconsoleHandler();
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
	}

	private void start() throws CloudsyncException, UsageException
	{

		options.parse();

		String logpath = options.getLogfilePath();
		if (logpath != null)
		{
			final Logger logger = Logger.getLogger("cloudsync");
			FileHandler fh;
			try
			{
				Path logfilePath = Paths.get(logpath);
				if (Files.exists(logfilePath))
				{
					Path preservedPath = Paths.get(logpath + ".1");
					Files.move(logfilePath, preservedPath, StandardCopyOption.REPLACE_EXISTING);
				}
				fh = new LogfileHandler(logpath);
				fh.setFormatter(new LogfileFormatter());
				logger.addHandler(fh);
			}
			catch (SecurityException e)
			{
				throw new CloudsyncException("Unexpected error on logfile creation", e);
			}
			catch (IOException e)
			{
				throw new CloudsyncException("Unexpected error on logfile creation", e);
			}
		}

		String name = options.getName();

		final LocalFilesystemConnector localConnection = new LocalFilesystemConnector(options);

		Handler handler = null;

		try
		{
			String remoteConnectorName = options.getRemoteConnector();
			RemoteConnector remoteConnector = null;
			try
			{
				remoteConnector = (RemoteConnector) Class.forName("cloudsync.connector.Remote" + remoteConnectorName + "Connector").newInstance();
			}
			catch (IllegalAccessException e)
			{
			}
			catch (InstantiationException e)
			{
			}
			catch (ClassNotFoundException e)
			{
				throw new CloudsyncException("Remote connector '" + remoteConnectorName + "' not found", e);
			}

			remoteConnector.init(name, options);

			final long start = System.currentTimeMillis();

			SyncType type = options.getType();
			String[] includePatterns = options.getIncludePatterns();
			if (includePatterns != null)
			{
				LOGGER.log(Level.FINEST, "use include pattern: " + "[^" + StringUtils.join(includePatterns, "$] | [$") + "$]");
			}
			String[] excludePatterns = options.getExcludePatterns();
			if (excludePatterns != null)
			{
				LOGGER.log(Level.FINEST, "use exclude pattern: " + "[^" + StringUtils.join(excludePatterns, "$] | [$") + "$]");
			}

			handler = new Handler(name, localConnection, remoteConnector, new Crypt(options), options.getExistingBehavior(), options.getFollowLinks(),
					options.getPermissionType());
			handler.init(type, options.getCacheFile(), options.getLockFile(), options.getPIDFile(), options.getNoCache(), options.getForceStart());

			switch ( type )
			{
				case BACKUP:
					handler.backup(!options.isDryRun(), includePatterns, excludePatterns);
					break;
				case RESTORE:
					handler.restore(!options.isDryRun(), includePatterns, excludePatterns);
					break;
				case LIST:
					handler.list(includePatterns, excludePatterns);
					break;
				case CLEAN:
					handler.clean();
					break;
			}

			final long end = System.currentTimeMillis();

			LOGGER.log(Level.INFO, "runtime: " + ((end - start) / 1000.0f) + " seconds");
		}
		finally
		{
			try
			{
				if (handler != null) handler.finalize();
			}
			catch (CloudsyncException e)
			{
				throw e;
			}
		}
	}

	public static void main(final String[] args) throws ParseException
	{

		final Cloudsync cloudsync = new Cloudsync(args);
		try
		{
			cloudsync.start();
		}
		catch (UsageException e)
		{
			if (!StringUtils.isEmpty(e.getMessage()))
			{
				LOGGER.log(Level.WARNING, e.getMessage() + "\n");
			}
			cloudsync.options.printHelp();
		}
		catch (CloudsyncException e)
		{
			LOGGER.log(Level.WARNING, e.getMessage() + "\n");
			if (e.getCause() != null)
			{
				e.printStackTrace();
			}
		}
	}
}
