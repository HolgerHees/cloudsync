package cloudsync;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import cloudsync.connector.LocalFilesystemConnector;
import cloudsync.connector.RemoteGoogleDriveConnector;
import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.UsageException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Crypt;
import cloudsync.helper.Helper;
import cloudsync.helper.Structure;
import cloudsync.logging.LogconsoleHandler;
import cloudsync.logging.LogfileFormatter;
import cloudsync.logging.LogfileHandler;

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
		if( logpath != null ){
			
			final Logger logger = Logger.getLogger("cloudsync");
			FileHandler fh;
			try {
				fh = new LogfileHandler(logpath);
				fh.setFormatter(new LogfileFormatter());
				logger.addHandler(fh);
			} catch (SecurityException e) {
				throw new CloudsyncException("Unexpected error on logfile creation",e);
			} catch (IOException e) {
				throw new CloudsyncException("Unexpected error on logfile creation",e);
			}
		}

		String name = options.getName();

		final LocalFilesystemConnector localConnection = new LocalFilesystemConnector(options.getPath());
		final RemoteGoogleDriveConnector remoteConnection = new RemoteGoogleDriveConnector(options.getProperty("REMOTE_CLIENT_ID"), options.getProperty("REMOTE_CLIENT_SECRET"),
				Helper.getPathProperty(options, "REMOTE_CLIENT_TOKEN_PATH"), options.getProperty("REMOTE_DIR"), name, options.getHistory());

		Structure structure = null;
		try {

			final long start = System.currentTimeMillis();

			String type = options.getType();
			String[] includePatterns = options.getIncludePatterns();
			if( includePatterns != null ){
				LOGGER.log(Level.FINEST,"use include pattern: " + "[^"+StringUtils.join(includePatterns,"$] | [$")+"$]" );
			}
			String[] excludePatterns = options.getExcludePatterns();
			if( excludePatterns != null ){
				LOGGER.log(Level.FINEST,"use exclude pattern: " + "[^"+StringUtils.join(excludePatterns,"$] | [$")+"$]" );
			}

			structure = new Structure(name, localConnection, remoteConnection, new Crypt(options.getProperty("PASSPHRASE")), options.getDuplicate(), options.getFollowLinks(),
					options.getNoPermission());
			structure.init(Helper.getPathProperty(options, "CACHE_FILE"), Helper.getPathProperty(options, "LOCK_FILE"), Helper.getPathProperty(options, "PID_FILE"), options.getNoCache(),
					options.getForceStart());

			if (type.equals("backup")) {
				structure.backup(!options.isTestRun(),includePatterns,excludePatterns);
			} else if (type.equals("restore")) {
				structure.restore(!options.isTestRun(),includePatterns,excludePatterns);
			} else if (type.equals("list")) {
				structure.list(includePatterns,excludePatterns);
			} else if (type.equals("clean")) {
				structure.clean();
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
