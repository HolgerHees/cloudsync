package cloudsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import cloudsync.connector.LocalFilesystemConnector;
import cloudsync.connector.RemoteGoogleDriveConnector;
import cloudsync.helper.CloudsyncException;
import cloudsync.helper.Crypt;
import cloudsync.helper.LogfileFormatter;
import cloudsync.helper.Structure;
import cloudsync.model.DuplicateType;
import cloudsync.model.Item;
import cloudsync.model.LinkType;

public class Cloudsync {

	private final static Logger LOGGER = Logger.getLogger(Cloudsync.class.getName());

	private final Options options;
	private final CommandLine cmd;

	public Cloudsync(final String[] args) throws ParseException {

		options = new Options();
		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Create or refresh backup of <path>");
		OptionBuilder.withLongOpt("backup");
		Option option = OptionBuilder.create("b");
		options.addOption(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Restore a backup into <path>");
		OptionBuilder.withLongOpt("restore");
		option = OptionBuilder.create("r");
		options.addOption(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Repair 'cloudsync*.cache' file and put leftover file into <path>");
		OptionBuilder.withLongOpt("clean");
		option = OptionBuilder.create("c");
		options.addOption(option);

		OptionBuilder.withArgName("name");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Backup name");
		OptionBuilder.withLongOpt("name");
		option = OptionBuilder.create("n");
		options.addOption(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Config file path. Default is /etc/cloudsync, ~/.cloudsync.config");
		OptionBuilder.withLongOpt("config");
		option = OptionBuilder.create();
		options.addOption(option);

		String description = "How to handle symbolic links\n";
		description += "<extern> - convert external links where the target is not part of the current backup structure - (default)\n";
		description += "<all> - convert all symbolic links\n";
		description += "<none> - convert no symbolic links to real files or folders";
		OptionBuilder.withArgName("extern|all|none");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("followlinks");
		option = OptionBuilder.create();
		options.addOption(option);

		description = "Behavior on existing files\n";
		description += "<stop> - stop immediately - (default)\n";
		description += "<update> - replace file\n";
		description += "<rename> - extend the name with an autoincrement number";
		OptionBuilder.withArgName("stop|update|rename");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("duplicate");
		option = OptionBuilder.create();
		options.addOption(option);

		description = "Before remove or update a file or folder move it to a history folder.\n";
		description += "Use a maximum of <count> history folders";
		OptionBuilder.withArgName("count");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("history");
		option = OptionBuilder.create();
		options.addOption(option);

		options.addOption(null, "nopermissions", false, "Don't restore permission, group and owner attributes");
		options.addOption(null, "nocache", false, "Don't use 'cloudsync*.cache' file (much slower)");

		options.addOption("h", "help", false, "Show this help");

		final CommandLineParser parser = new GnuParser();
		cmd = parser.parse(options, args);
	}

	private void start() {

		final Logger logger = Logger.getLogger("cloudsync");
		logger.setLevel(Level.ALL);
		final ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new LogfileFormatter());
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);

		String type = null;
		String path = null;
		if ((path = cmd.getOptionValue("backup")) != null) {
			type = "backup";
		} else if ((path = cmd.getOptionValue("restore")) != null) {
			type = "restore";
		} else if ((path = cmd.getOptionValue("clean")) != null) {
			type = "clean";
		}

		final String name = cmd.getOptionValue("name");

		final LinkType followlinks = LinkType.fromName(cmd.getOptionValue("followlinks", LinkType.EXTERNAL.getName()));
		final DuplicateType duplicate = DuplicateType.fromName(cmd.getOptionValue("duplicate", DuplicateType.STOP.getName()));

		String config = cmd.getOptionValue("config");
		if (config.startsWith("." + Item.SEPARATOR)) {
			config = System.getProperty("user.dir") + Item.SEPARATOR + config;
		}

		final Integer history = Integer.parseInt(cmd.getOptionValue("history", "0"));

		final boolean nopermissions = cmd.hasOption("nopermissions");
		final boolean nocache = cmd.hasOption("nocache");

		final boolean baseValid = path != null && new File(path).isDirectory();
		boolean configValid = config != null && new File(config).isFile();

		final Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(config));
		} catch (final IOException e) {
			configValid = false;
		}

		if (cmd.hasOption("help") || type == null || name == null || followlinks == null || duplicate == null || !baseValid || config == null || !configValid) {

			System.out.println("");
			System.out.println("error: missing or wrong options");
			if (type == null) {
				System.out.println(" You must specifiy --backup, --restore or --clean <path>");
			} else if (!baseValid) {
				System.out.println(" --" + type + " <path> not valid");
			}
			if (name == null) {
				System.out.println(" Missing --name <name>");
			}
			if (followlinks == null) {
				System.out.println(" Wrong --followlinks behavior set");
			}
			if (duplicate == null) {
				System.out.println(" Wrong --duplicate behavior set");
			}
			if (config == null) {
				System.out.println(" Missing --config <path>");
			} else if (!configValid) {
				System.out.println(" --config <path> not valid");
			}
			System.out.println("");

			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("cloudsync <options>", options);
			return;
		}

		try {

			// /home/hhees/Tools/jdk1.7.0_55/bin/java -cp
			// "./bin/:./lib/*:./lib/drive/*:./lib/drive/libs/*"
			// cloudsync.Cloudsync --help

			final String[] propertyNames = new String[] { "REMOTE_CLIENT_ID", "REMOTE_CLIENT_SECRET", "REMOTE_CLIENT_TOKEN_PATH", "REMOTE_DIR", "PASSPHRASE", "MAX_CACHE_FILE_AGE", "CACHE_FILE" };
			for (final String propertyName : propertyNames) {
				if (StringUtils.isEmpty(prop.getProperty(propertyName))) {
					throw new CloudsyncException("'" + propertyName + "' is not configured");
				}
			}

			String _cacheFilePath = prop.getProperty("CACHE_FILE");
			if (_cacheFilePath.startsWith("." + Item.SEPARATOR)) {
				_cacheFilePath = System.getProperty("user.dir") + Item.SEPARATOR + _cacheFilePath;
			}
			_cacheFilePath = _cacheFilePath.replace("{name}", name);
			final Path cacheFilePath = Paths.get(_cacheFilePath);
			final Crypt crypt = new Crypt(prop.getProperty("PASSPHRASE"));

			String tokenFilePath = prop.getProperty("REMOTE_CLIENT_TOKEN_PATH");
			if (tokenFilePath.startsWith("." + Item.SEPARATOR)) {
				tokenFilePath = System.getProperty("user.dir") + Item.SEPARATOR + tokenFilePath;
			}

			final LocalFilesystemConnector localConnection = new LocalFilesystemConnector(path);
			final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.d_H.m.s");
			final RemoteGoogleDriveConnector remoteConnection = new RemoteGoogleDriveConnector(prop.getProperty("REMOTE_CLIENT_ID"), prop.getProperty("REMOTE_CLIENT_SECRET"), tokenFilePath,
					prop.getProperty("REMOTE_DIR"), name, history > 0 ? name + " " + sdf.format(new Date()) : null);
			final Structure structure = new Structure(localConnection, remoteConnection, crypt, duplicate, followlinks, nopermissions, history);

			final long start = System.currentTimeMillis();

			if (type.equals("backup")) {
				Cloudsync.backup(structure, cacheFilePath, Integer.parseInt(prop.getProperty("MAX_CACHE_FILE_AGE")), nocache);
			} else if (type.equals("restore")) {
				Cloudsync.restore(structure, cacheFilePath);
			} else if (type.equals("clean")) {
				Cloudsync.clean(structure, cacheFilePath);
			}

			final long end = System.currentTimeMillis();

			LOGGER.log(Level.INFO, "\nruntime: " + ((end - start) / 1000.0f) + " seconds");
		} catch (final CloudsyncException e) {

			LOGGER.log(Level.INFO, "\nERROR: " + e.getLocalizedMessage() + "\n");
			if (e.getCause() != null) {
				e.printStackTrace();
			}
		}
	}

	private static void clean(final Structure structure, final Path cacheFilePath) throws CloudsyncException {

		LOGGER.log(Level.INFO, "load structure from server");
		structure.buildStructureFromRemoteConnection();

		LOGGER.log(Level.INFO, "start clean");
		structure.clean();
		structure.saveChangedStructureToFile(cacheFilePath);
	}

	private static void restore(final Structure structure, final Path cacheFilePath) throws CloudsyncException {

		LOGGER.log(Level.INFO, "load structure from server");
		structure.buildStructureFromRemoteConnection();

		LOGGER.log(Level.INFO, "start restore");
		structure.restore(true);
		structure.saveChangedStructureToFile(cacheFilePath);
	}

	private static void backup(final Structure structure, final Path cacheFilePath, final Integer maxCacheAge, final Boolean nocache) throws CloudsyncException {

		if (Files.exists(cacheFilePath) && !nocache.booleanValue()) {

			if (!FileUtils.isFileNewer(cacheFilePath.toFile(), new Date().getTime() - (maxCacheAge * 24 * 60 * 60) * 1000)) {

				LOGGER.log(Level.INFO, "state file is older than " + maxCacheAge + " days. force a server refresh.");
				try {
					Files.delete(cacheFilePath);
				} catch (final IOException e) {
					throw new CloudsyncException("Can't delete cache file", e);
				}
				structure.buildStructureFromRemoteConnection();
			} else {

				LOGGER.log(Level.INFO, "load structure from cache");
				structure.buildStructureFromFile(cacheFilePath);
			}
		} else {

			LOGGER.log(Level.INFO, "load structure from server");
			structure.buildStructureFromRemoteConnection();
		}

		LOGGER.log(Level.INFO, "start sync");
		structure.backup(true);
		structure.saveChangedStructureToFile(cacheFilePath);
	}

	public static void main(final String[] args) throws ParseException {

		final Cloudsync cloudsync = new Cloudsync(args);
		cloudsync.start();
	}
}
