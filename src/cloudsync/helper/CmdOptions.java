package cloudsync.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.UsageException;
import cloudsync.model.DuplicateType;
import cloudsync.model.Item;
import cloudsync.model.LinkType;
import cloudsync.model.SyncType;

public class CmdOptions {

	private final Options options;
	private final List<Option> positions;
	private final String[] args;

	private String passphrase;

	private Properties prop;
	private SyncType type;
	private String path;
	private String name;
	private Integer history;
	private String[] includePatterns;
	private String[] excludePatterns;
	private String logfilePath;
	private String cachefilePath;
	private String lockfilePath;
	private String pidfilePath;
	private boolean nopermissions;
	private boolean nocache;
	private boolean forcestart;
	private boolean testrun;
	private LinkType followlinks;
	private DuplicateType duplicate;

	public CmdOptions(final String[] args) {

		this.args = args;

		positions = new ArrayList<Option>();

		options = new Options();
		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Create or refresh backup of <path>");
		OptionBuilder.withLongOpt(SyncType.BACKUP.getName());
		Option option = OptionBuilder.create("b");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Restore a backup into <path>");
		OptionBuilder.withLongOpt(SyncType.RESTORE.getName());
		option = OptionBuilder.create("r");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Repair 'cloudsync*.cache' file and put leftover file into <path>");
		OptionBuilder.withLongOpt(SyncType.CLEAN.getName());
		option = OptionBuilder.create("c");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("List the contents of an backup");
		OptionBuilder.withLongOpt(SyncType.LIST.getName());
		option = OptionBuilder.create("l");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("name");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Backup name of --backup, --restore, --clean or --list");
		OptionBuilder.withLongOpt("name");
		option = OptionBuilder.create("n");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Config file path. Default is './config/cloudsync.config'");
		OptionBuilder.withLongOpt("config");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		String description = "How to handle symbolic links\n";
		description += "<extern> - follow symbolic links if the target is outside from the current directory hierarchy - (default)\n";
		description += "<all> - follow all symbolic links\n";
		description += "<none> - don't follow any symbolic links";
		OptionBuilder.withArgName("extern|all|none");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("followlinks");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		description = "Behavior on existing files\n";
		description += "<stop> - stop immediately - (default for --backup and --restore)\n";
		description += "<update> - replace file\n";
		description += "<rename> - extend the name with an autoincrement number (default for --clean)";
		OptionBuilder.withArgName("stop|update|rename");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("duplicate");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		description = "Before remove or update a file or folder move it to a history folder.\n";
		description += "Use a maximum of <count> history folders";
		OptionBuilder.withArgName("count");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("history");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("pattern");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Include content of --backup, --restore and --list if the path matches the regex based ^<pattern>$. Multiple patterns can be separated with an '|' character.");
		OptionBuilder.withLongOpt("include");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("pattern");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Exclude content of --backup, --restore and --list if the path matches the regex based ^<pattern>$. Multiple patterns can be separated with an '|' character.");
		OptionBuilder.withLongOpt("exclude");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Don't restore permission, group and owner attributes");
		OptionBuilder.withLongOpt("nopermissions");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Don't use 'cloudsync*.cache' file for --backup or --list (much slower)");
		OptionBuilder.withLongOpt("nocache");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Ignore a existing pid file. Should only be used after a previous crashed job.");
		OptionBuilder.withLongOpt("forcestart");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Log message to <path>");
		OptionBuilder.withLongOpt("logfile");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Cache data to <path>");
		OptionBuilder.withLongOpt("cachefile");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Start a 'test' run of --backup or --restore.");
		OptionBuilder.withLongOpt("test");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Show this help");
		OptionBuilder.withLongOpt("help");
		option = OptionBuilder.create("h");
		options.addOption(option);
		positions.add(option);
	}

	public void parse() throws UsageException, CloudsyncException {

		final CommandLineParser parser = new GnuParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {

			throw new UsageException(e.getMessage());
		}

		type = null;
		path = null;
		if ((path = cmd.getOptionValue(SyncType.BACKUP.getName())) != null) {
			type = SyncType.BACKUP;
		} else if ((path = cmd.getOptionValue(SyncType.RESTORE.getName())) != null) {
			type = SyncType.RESTORE;
		} else if ((path = cmd.getOptionValue(SyncType.CLEAN.getName())) != null) {
			type = SyncType.CLEAN;
		} else if (cmd.hasOption(SyncType.LIST.getName())) {
			type = SyncType.LIST;
		}

		String config = cmd.getOptionValue("config", "." + Item.SEPARATOR + "config" + Item.SEPARATOR + "cloudsync.config");
		if (config.startsWith("." + Item.SEPARATOR)) {
			config = System.getProperty("user.dir") + Item.SEPARATOR + config;
		}

		boolean configValid = config != null && new File(config).isFile();
		prop = new Properties();
		try {
			prop.load(new FileInputStream(config));
		} catch (final IOException e) {
			configValid = false;
		}

		name = getOptionValue(cmd, "name", null);

		passphrase = prop.getProperty("PASSPHRASE");
		if (StringUtils.isEmpty(passphrase)) {
			throw new CloudsyncException("'PASSPHRASE' is not configured");
		}
		String value = getOptionValue(cmd, "followlinks", LinkType.EXTERNAL.getName());
		followlinks = LinkType.fromName(value);
		value = getOptionValue(cmd, "duplicate", SyncType.CLEAN.equals(type) ? DuplicateType.RENAME.getName() : DuplicateType.STOP.getName());
		duplicate = DuplicateType.fromName(value);

		history = (type != null && type.equals("backup")) ? Integer.parseInt(getOptionValue(cmd, "history", "0")) : 0;

		nopermissions = cmd.hasOption("nopermissions");
		nocache = cmd.hasOption("nocache") || SyncType.CLEAN.equals(type);
		forcestart = cmd.hasOption("forcestart");
		testrun = cmd.hasOption("test");
		String pattern = getOptionValue(cmd, "include", null);
		if (pattern != null)
			includePatterns = pattern.contains("|") ? pattern.split("\\|") : new String[] { pattern };
		pattern = getOptionValue(cmd, "exclude", null);
		if (pattern != null)
			excludePatterns = pattern.contains("|") ? pattern.split("\\|") : new String[] { pattern };

		if (!StringUtils.isEmpty(name)) {
			logfilePath = Helper.preparePath(getOptionValue(cmd, "logfile", null), name);
			cachefilePath = Helper.preparePath(getOptionValue(cmd, "cachefile", null), name);
			pidfilePath = cachefilePath.substring(0, cachefilePath.lastIndexOf(".")) + ".pid";
			lockfilePath = cachefilePath.substring(0, cachefilePath.lastIndexOf(".")) + ".lock";
		}

		final boolean baseValid = SyncType.LIST.equals(type) || (path != null && new File(path).isDirectory());
		boolean logfileValid = logfilePath == null || new File(logfilePath).getParentFile().isDirectory();
		boolean cachefileValid = cachefilePath == null || new File(cachefilePath).getParentFile().isDirectory();

		if (cmd.hasOption("help") || type == null || name == null || followlinks == null || duplicate == null || !baseValid || config == null || !configValid || !logfileValid || !cachefileValid) {

			List<String> messages = new ArrayList<String>();
			if (cmd.getOptions().length > 0) {

				messages.add("error: missing or wrong options");
				if (type == null) {
					messages.add(" You must specifiy --backup, --restore, --list or --clean");
				} else if (!baseValid) {
					messages.add(" --" + type.getName() + " <path> not valid");
				}
				if (name == null) {
					messages.add(" Missing --name <name>");
				}
				if (followlinks == null) {
					messages.add(" Wrong --followlinks behavior set");
				}
				if (duplicate == null) {
					messages.add(" Wrong --duplicate behavior set");
				}
				if (config == null) {
					messages.add(" Missing --config <path>");
				} else if (!configValid) {
					messages.add(" --config <path> not valid");
				}
				if (!logfileValid) {
					messages.add(" --logfile <path> not valid");
				}
				if (!cachefileValid) {
					messages.add(" --cachefile <path> not valid");
				}
			}
			throw new UsageException(StringUtils.join(messages, '\n'));
		}
	}

	public String getOptionValue(CommandLine cmd, String key, String defaultValue) {
		String value = cmd.getOptionValue(key);
		if (!StringUtils.isEmpty(value))
			return value;
		value = prop.getProperty(key.toUpperCase());
		if (!StringUtils.isEmpty(value))
			return value;
		return defaultValue;
	}

	public void printHelp() {
		final HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(120);
		formatter.setOptionComparator(new Comparator<Option>() {

			@Override
			public int compare(Option o1, Option o2) {
				if (positions.indexOf(o1) < positions.indexOf(o2))
					return -1;
				if (positions.indexOf(o1) > positions.indexOf(o2))
					return 1;
				return 0;
			}
		});
		// formatter.setOptPrefix("");
		formatter.printHelp("cloudsync <options>", options);
	}

	public String getPath() {
		return path;
	}

	public SyncType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String[] getIncludePatterns() {
		return includePatterns;
	}

	public String[] getExcludePatterns() {
		return excludePatterns;
	}

	public Integer getHistory() {
		return history;
	}

	public String getLogfilePath() {
		return logfilePath;
	}

	public boolean getNoPermission() {
		return nopermissions;
	}

	public boolean getNoCache() {
		return nocache;
	}

	public boolean getForceStart() {
		return forcestart;
	}

	public boolean isTestRun() {
		return testrun;
	}

	public LinkType getFollowLinks() {
		return followlinks;
	}

	public DuplicateType getDuplicate() {
		return duplicate;
	}

	public String getProperty(String key) {
		return prop.getProperty(key);
	}

	public String getCacheFile() {
		return cachefilePath;
	}

	public String getLockFile() {
		return lockfilePath;
	}

	public String getPIDFile() {
		return pidfilePath;
	}

	public String getPassphrase() {
		return passphrase;
	}
}
