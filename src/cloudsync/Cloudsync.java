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
import cloudsync.connector.RemoteGoogleDriveConnector;
import cloudsync.connector.RemoteGoogleDriveOptions;
import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.UsageException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Crypt;
import cloudsync.helper.Structure;
import cloudsync.logging.LogconsoleHandler;
import cloudsync.logging.LogfileFormatter;
import cloudsync.logging.LogfileHandler;
import cloudsync.model.SyncType;

public class Cloudsync {

	private final static Logger LOGGER = Logger.getLogger(Cloudsync.class.getName());

	private final CmdOptions options;

	public Cloudsync(final String[] args) {

		this.options = new CmdOptions(args);

		final Logger logger = Logger.getLogger("cloudsync");
		logger.setLevel(Level.ALL);
		final ConsoleHandler handler = new LogconsoleHandler();
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
	}

	private void start() throws CloudsyncException, UsageException {

		options.parse();

		String logpath = options.getLogfilePath();
		if (logpath != null) {

			final Logger logger = Logger.getLogger("cloudsync");
			FileHandler fh;
			try {
				Path logfilePath = Paths.get(logpath);
				if (Files.exists(logfilePath)) {
					Path preservedPath = Paths.get(logpath + ".1");
					Files.move(logfilePath, preservedPath, StandardCopyOption.REPLACE_EXISTING);
				}
				fh = new LogfileHandler(logpath);
				fh.setFormatter(new LogfileFormatter());
				logger.addHandler(fh);
			} catch (SecurityException e) {
				throw new CloudsyncException("Unexpected error on logfile creation", e);
			} catch (IOException e) {
				throw new CloudsyncException("Unexpected error on logfile creation", e);
			}
		}

		String name = options.getName();

		final LocalFilesystemConnector localConnection = new LocalFilesystemConnector(options.getPath());
		final RemoteGoogleDriveConnector remoteConnection = new RemoteGoogleDriveConnector(new RemoteGoogleDriveOptions(options, name), name, options.getHistory());

		Structure structure = null;
		try {

			final long start = System.currentTimeMillis();

			SyncType type = options.getType();
			String[] includePatterns = options.getIncludePatterns();
			if (includePatterns != null) {
				LOGGER.log(Level.FINEST, "use include pattern: " + "[^" + StringUtils.join(includePatterns, "$] | [$") + "$]");
			}
			String[] excludePatterns = options.getExcludePatterns();
			if (excludePatterns != null) {
				LOGGER.log(Level.FINEST, "use exclude pattern: " + "[^" + StringUtils.join(excludePatterns, "$] | [$") + "$]");
			}

			structure = new Structure(name, localConnection, remoteConnection, new Crypt(options.getPassphrase()), options.getDuplicate(), options.getFollowLinks(), options.getNoPermission());
			structure.init(type, options.getCacheFile(), options.getLockFile(), options.getPIDFile(), options.getNoCache(), options.getForceStart());

			switch (type) {
			case BACKUP:
				structure.backup(!options.isTestRun(), includePatterns, excludePatterns);
				break;
			case RESTORE:
				structure.restore(!options.isTestRun(), includePatterns, excludePatterns);
				break;
			case LIST:
				structure.list(includePatterns, excludePatterns);
				break;
			case CLEAN:
				structure.clean();
				break;
			}

			final long end = System.currentTimeMillis();

			LOGGER.log(Level.INFO, "runtime: " + ((end - start) / 1000.0f) + " seconds");
		} finally {

			try {
				if (structure != null)
					structure.finalize();
			} catch (CloudsyncException e) {
				throw e;
			}
		}
	}

	public static void main(final String[] args) throws ParseException {

		final Cloudsync cloudsync = new Cloudsync(args);
		try {
			cloudsync.start();
		} catch (UsageException e) {
			if (!StringUtils.isEmpty(e.getMessage())) {
				LOGGER.log(Level.INFO, "error: " + e.getMessage() + "\n");
			}
			cloudsync.options.printHelp();
		} catch (CloudsyncException e) {
			LOGGER.log(Level.INFO, "error: " + e.getMessage() + "\n");
			if (e.getCause() != null) {
				e.printStackTrace();
			}
		}
	}
}
