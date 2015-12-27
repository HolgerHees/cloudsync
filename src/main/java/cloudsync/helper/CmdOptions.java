package cloudsync.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import cloudsync.exceptions.InfoException;
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
import cloudsync.model.ExistingBehaviorType;
import cloudsync.model.Item;
import cloudsync.model.LinkType;
import cloudsync.model.PermissionType;
import cloudsync.model.SyncType;

public class CmdOptions
{
	private final Options			options;
	private final List<Option>		positions;
	private final String[]			args;

	private String					passphrase;

	private Properties				prop;
	private SyncType				type;
	private String					path;
	private String					name;
	private Integer					history;
	private String[]				includePatterns;
	private String[]				excludePatterns;
	private String					logfilePath;
	private String					cachefilePath;
	private String					lockfilePath;
	private String					pidfilePath;
	private PermissionType			permissions;
	private boolean					nocache;
	private boolean					forcestart;
	private boolean					dryrun;
	private boolean					showProgress;
	private boolean					askToContinue;
	private boolean					logAndContinue;
	private boolean					noencryption;
	private LinkType				followlinks;
	private ExistingBehaviorType	existingBehavior;
	private String					remoteConnector;

	private int						retries;
	private int						waitretry;

	private long 					minTmpFileSize;

	public CmdOptions(final String[] args)
	{
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

		description = "Behavior on files that exists localy during --restore\n";
		description += "<stop> - stop immediately - (default)\n";
		description += "<update> - replace file\n";
		description += "<skip> - skip file\n";
		description += "<rename> - extend the name with an autoincrement number";
		OptionBuilder.withArgName("stop|update|skip|rename");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("existing");
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
		OptionBuilder
				.withDescription("Include content of --backup, --restore and --list if the path matches the regex based ^<pattern>$. Multiple patterns can be separated with an '|' character.");
		OptionBuilder.withLongOpt("include");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("pattern");
		OptionBuilder.hasArg();
		OptionBuilder
				.withDescription("Exclude content of --backup, --restore and --list if the path matches the regex based ^<pattern>$. Multiple patterns can be separated with an '|' character.");
		OptionBuilder.withLongOpt("exclude");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		description = "Behavior how to handle acl permissions during --restore\n";
		description += "<set> - set all permissions and ownerships - (default)\n";
		description += "<ignore> - ignores all permissions and ownerships\n";
		description += "<try> - ignores invalid and not assignable permissions and ownerships\n";
		OptionBuilder.withArgName("set|ignore|try");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("permissions");
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

		OptionBuilder.withDescription("Don't encrypt uploaded data");
		OptionBuilder.withLongOpt("noencryption");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Perform a trial run of --backup or --restore with no changes made.");
		OptionBuilder.withLongOpt("dry-run");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Show progress during transfer and encryption.");
		OptionBuilder.withLongOpt("progress");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Number of network operation retries before an error is thrown (default: 6).");
		OptionBuilder.withLongOpt("retries");
		OptionBuilder.withArgName("number");
		OptionBuilder.hasArg();
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Number of seconds between 2 retries (default: 10).");
		OptionBuilder.withLongOpt("waitretry");
		OptionBuilder.withArgName("seconds");
		OptionBuilder.hasArg();
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		description = "How to continue on network problems\n";
		description += "<exception> - Throw an exception - (default)\n";
		description += "<ask> - Show a command prompt (Y/n) to continue\n";
		OptionBuilder.withArgName("exception|ask");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("network-error");
		option = OptionBuilder.create();
		options.addOption(option);
		positions.add(option);

		description = "How to continue on blocked files or permission problems\n";
		description += "<exception> - Throw an exception - (default)\n";
		description += "<message> - Show a error log message\n";
		OptionBuilder.withArgName("exception|message");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription(description);
		OptionBuilder.withLongOpt("file-error");
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

		OptionBuilder.withDescription("Show version number");
		OptionBuilder.withLongOpt("version");
		option = OptionBuilder.create("v");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("Show this help");
		OptionBuilder.withLongOpt("help");
		option = OptionBuilder.create("h");
		options.addOption(option);
		positions.add(option);
	}

	public void parse() throws UsageException, CloudsyncException, InfoException
	{
		final CommandLineParser parser = new GnuParser();
		CommandLine cmd;
		try
		{
			cmd = parser.parse(options, args);
		}
		catch (ParseException e)
		{
			throw new UsageException(e.getMessage());
		}

		type = null;
		path = null;
		if ((path = cmd.getOptionValue(SyncType.BACKUP.getName())) != null)
		{
			type = SyncType.BACKUP;
		}
		else if ((path = cmd.getOptionValue(SyncType.RESTORE.getName())) != null)
		{
			type = SyncType.RESTORE;
		}
		else if ((path = cmd.getOptionValue(SyncType.CLEAN.getName())) != null)
		{
			type = SyncType.CLEAN;
		}
		else if (cmd.hasOption(SyncType.LIST.getName()))
		{
			type = SyncType.LIST;
		}

		String config = cmd.getOptionValue("config", "." + Item.SEPARATOR + "config" + Item.SEPARATOR + "cloudsync.config");
		if (config.startsWith("." + Item.SEPARATOR))
		{
			config = System.getProperty("user.dir") + Item.SEPARATOR + config;
		}

		boolean configValid = config != null && new File(config).isFile();
		prop = new Properties();
		try
		{
			prop.load(new FileInputStream(config));
		}
		catch (final IOException e)
		{
			configValid = false;
		}

		name = getOptionValue(cmd, "name", null);

		remoteConnector = prop.getProperty("REMOTE_CONNECTOR");

		String value = getOptionValue(cmd, "followlinks", LinkType.EXTERNAL.getName());
		followlinks = LinkType.fromName(value);
		value = getOptionValue(cmd, "existing", SyncType.CLEAN.equals(type) ? ExistingBehaviorType.RENAME.getName() : ExistingBehaviorType.STOP.getName());
		existingBehavior = ExistingBehaviorType.fromName(value);
		value = getOptionValue(cmd, "permissions", PermissionType.SET.getName());
		permissions = PermissionType.fromName(value);

		history = SyncType.BACKUP.equals(type) ? Integer.parseInt(getOptionValue(cmd, "history", "0")) : 0;

		try
		{
			retries = Integer.parseInt(getOptionValue(cmd, "retries", "6"));
		}
		catch (NumberFormatException e)
		{
			retries = 0;
		}

		try
		{
			waitretry = Integer.parseInt(getOptionValue(cmd, "waitretry", "10"));
		}
		catch (NumberFormatException e)
		{
			waitretry = 0;
		}

		try
		{
			minTmpFileSize = Long.parseLong( getOptionValue(cmd, "min_tmp_file_size", "134217728" ) );
		}
		catch (NumberFormatException e)
		{
			// 128MB
			minTmpFileSize = 134217728;
		}

		value = getOptionValue(cmd, "network-error", "exception");
		askToContinue = value.equals("ask");

		value = getOptionValue(cmd, "file-error", "exception");
		logAndContinue = value.equals("message");

		nocache = cmd.hasOption("nocache") || SyncType.CLEAN.equals(type);
		forcestart = cmd.hasOption("forcestart");
		dryrun = cmd.hasOption("dry-run");
		showProgress = cmd.hasOption("progress");
		noencryption = cmd.hasOption("noencryption");

		String pattern = getOptionValue(cmd, "include", null);
		if (pattern != null) includePatterns = pattern.contains("|") ? pattern.split("\\|") : new String[] { pattern };
		pattern = getOptionValue(cmd, "exclude", null);
		if (pattern != null) excludePatterns = pattern.contains("|") ? pattern.split("\\|") : new String[] { pattern };

		if (!StringUtils.isEmpty(name))
		{
			logfilePath = Helper.preparePath(getOptionValue(cmd, "logfile", null), name);
			cachefilePath = Helper.preparePath(getOptionValue(cmd, "cachefile", null), name);
			if( !StringUtils.isEmpty(cachefilePath))
			{
				pidfilePath = cachefilePath.substring(0, cachefilePath.lastIndexOf(".")) + ".pid";
				lockfilePath = cachefilePath.substring(0, cachefilePath.lastIndexOf(".")) + ".lock";
			}
		}

		final boolean baseValid = SyncType.LIST.equals(type) || (path != null && new File(path).isDirectory());
		boolean logfileValid = logfilePath == null || new File(logfilePath).getParentFile().isDirectory();
		boolean cachefileValid = cachefilePath == null || new File(cachefilePath).getParentFile().isDirectory();

		if( cmd.hasOption("version") )
		{
			throw new InfoException("cloudsync " + getClass().getPackage().getImplementationVersion());
		}
		else if (cmd.hasOption("help") || type == null || name == null || followlinks == null || existingBehavior == null || retries == 0 || waitretry == 0
				|| permissions == null || !baseValid || config == null || !configValid || !logfileValid || !cachefileValid)
		{
			int possibleWrongOptions = cmd.getOptions().length;
			if (cmd.hasOption("help")) possibleWrongOptions--;

			List<String> messages = new ArrayList<String>();
			if (possibleWrongOptions > 0)
			{
				messages.add("missing or wrong options\nerror(s):");
				if (type == null)
				{
					messages.add(" You must specifiy --backup, --restore, --list or --clean");
				}
				else if (!baseValid)
				{
					messages.add(" --" + type.getName() + " <path> not valid");
				}
				if (name == null)
				{
					messages.add(" Missing --name <name>");
				}
				if (followlinks == null)
				{
					messages.add(" Wrong --followlinks <behavior> set");
				}
				if (existingBehavior == null)
				{
					messages.add(" Wrong --existing <behavior> set");
				}
				if (retries == 0)
				{
					messages.add(" Wrong --retries <number> set");
				}
				if (waitretry == 0)
				{
					messages.add(" Wrong --waitretry <seconds> set");
				}
				if (permissions == null)
				{
					messages.add(" Wrong --permissions <behavior> set");
				}
				if (config == null)
				{
					messages.add(" Missing --config <path>");
				}
				else if (!configValid)
				{
					messages.add(" --config <path> not valid");
				}
				if (!logfileValid)
				{
					messages.add(" --logfile <path> not valid");
				}
				if (!cachefileValid)
				{
					messages.add(" --cachefile <path> not valid");
				}
			}
			else if (!cmd.hasOption("help") && !configValid)
			{
				messages.add(" No config file found");
			}
			throw new UsageException(StringUtils.join(messages, '\n'));
		}

		passphrase = prop.getProperty("PASSPHRASE");
		if (StringUtils.isEmpty(passphrase))
		{
			throw new CloudsyncException("'PASSPHRASE' is not configured");
		}
	}

	public String getOptionValue(CommandLine cmd, String key, String defaultValue)
	{
		String value = cmd.getOptionValue(key);
		if (!StringUtils.isEmpty(value)) return value;
		value = prop.getProperty(key.toUpperCase());
		if (!StringUtils.isEmpty(value)) return value;
		return defaultValue;
	}

	public void printHelp()
	{
		final HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(120);
		formatter.setOptionComparator(new Comparator<Option>()
		{

			@Override
			public int compare(Option o1, Option o2)
			{
				if (positions.indexOf(o1) < positions.indexOf(o2)) return -1;
				if (positions.indexOf(o1) > positions.indexOf(o2)) return 1;
				return 0;
			}
		});
		// formatter.setOptPrefix("");
		formatter.printHelp("cloudsync <options>", options);
	}

	public String getPath()
	{
		return path;
	}

	public SyncType getType()
	{
		return type;
	}

	public String getName()
	{
		return name;
	}

	public String[] getIncludePatterns()
	{
		return includePatterns;
	}

	public String[] getExcludePatterns()
	{
		return excludePatterns;
	}

	public Integer getHistory()
	{
		return history;
	}

	public String getLogfilePath()
	{
		return logfilePath;
	}

	public PermissionType getPermissionType()
	{
		return permissions;
	}

	public boolean getNoCache()
	{
		return nocache;
	}

	public boolean getNoEncryption()
	{
		return noencryption;
	}

	public boolean getForceStart()
	{
		return forcestart;
	}

	public boolean isDryRun()
	{
		return dryrun;
	}

	public boolean showProgress()
	{
		return showProgress;
	}

	public boolean askToContinue()
	{
		return askToContinue;
	}

	public boolean logAndContinue()
	{
		return logAndContinue;
	}

	public int getRetries()
	{
		return retries;
	}

	public int getWaitRetry()
	{
		return waitretry;
	}

	public long getMinTmpFileSise()
	{
		return minTmpFileSize;
	}

	public LinkType getFollowLinks()
	{
		return followlinks;
	}

	public ExistingBehaviorType getExistingBehavior()
	{
		return existingBehavior;
	}

	public String getProperty(String key)
	{
		return prop.getProperty(key);
	}

	public String getCacheFile()
	{
		return cachefilePath;
	}

	public String getLockFile()
	{
		return lockfilePath;
	}

	public String getPIDFile()
	{
		return pidfilePath;
	}

	public String getPassphrase()
	{
		return passphrase;
	}

	public String getRemoteConnector()
	{
		return remoteConnector;
	}
}
