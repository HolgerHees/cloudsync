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

public class CmdOptions {

	private final Options options;
	private final List<Option> positions;
	private final String[] args;
	
	private Properties prop;
	private String type;
	private String path;
	private String name;
	private Integer history;
	private String limitPattern;
	private boolean nopermissions;
	private boolean nocache;
	private boolean forcestart;
	private LinkType followlinks;
	private DuplicateType duplicate;
	
	public CmdOptions( final String[] args ){
		
		this.args = args;
		
		positions = new ArrayList<Option>();

		options = new Options();
		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Create or refresh backup of <path>");
		OptionBuilder.withLongOpt("backup");
		Option option = OptionBuilder.create("b");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Restore a backup into <path>");
		OptionBuilder.withLongOpt("restore");
		option = OptionBuilder.create("r");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Repair 'cloudsync*.cache' file and put leftover file into <path>");
		OptionBuilder.withLongOpt("clean");
		option = OptionBuilder.create("c");
		options.addOption(option);
		positions.add(option);

		OptionBuilder.withDescription("List the contents of an backup");
		OptionBuilder.withLongOpt("list");
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

		OptionBuilder.withArgName("pattern");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Limit contents paths of --restore or --list to regex based ^<pattern>$");
		OptionBuilder.withLongOpt("limit");
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
		description += "<stop> - stop immediately - (default)\n";
		description += "<update> - replace file\n";
		description += "<rename> - extend the name with an autoincrement number";
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

		OptionBuilder.withDescription("Show this help");
		OptionBuilder.withLongOpt("help");
		option = OptionBuilder.create("h");
		options.addOption(option);
		positions.add(option);
	}
	
	public void parse() throws UsageException, CloudsyncException{
		
		final CommandLineParser parser = new GnuParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {

			throw new UsageException(e.getMessage());
		}
		
		type = null;
		path = null;
		if ((path = cmd.getOptionValue("backup")) != null) {
			type = "backup";
		} else if ((path = cmd.getOptionValue("restore")) != null) {
			type = "restore";
		} else if ((path = cmd.getOptionValue("clean")) != null) {
			type = "clean";
		} else if (cmd.hasOption("list")) {
			type = "list";
		}

		name = cmd.getOptionValue("name");

		followlinks = LinkType.fromName(cmd.getOptionValue("followlinks", LinkType.EXTERNAL.getName()));
		duplicate = DuplicateType.fromName(cmd.getOptionValue("duplicate", DuplicateType.STOP.getName()));

		String config = cmd.getOptionValue("config", "." + Item.SEPARATOR + "config" + Item.SEPARATOR + "cloudsync.config");
		if (config.startsWith("." + Item.SEPARATOR)) {
			config = System.getProperty("user.dir") + Item.SEPARATOR + config;
		}

		history = (type != null && type.equals("backup")) ? Integer.parseInt(cmd.getOptionValue("history", "0")) : 0;

		nopermissions = cmd.hasOption("nopermissions");
		nocache = cmd.hasOption("nocache");
		forcestart = cmd.hasOption("forcestart");
		limitPattern = cmd.getOptionValue("limit");

		final boolean baseValid = "list".equals(type) || (path != null && new File(path).isDirectory());
		boolean configValid = config != null && new File(config).isFile();

		prop = new Properties();
		try {
			prop.load(new FileInputStream(config));
		} catch (final IOException e) {
			configValid = false;
		}

		final String[] propertyNames = new String[] { "REMOTE_CLIENT_ID", "REMOTE_CLIENT_SECRET", "REMOTE_CLIENT_TOKEN_PATH", "REMOTE_DIR", "PASSPHRASE", "CACHE_FILE", "LOCK_FILE", "PID_FILE" };
		for (final String propertyName : propertyNames) {
			if (StringUtils.isEmpty(prop.getProperty(propertyName))) {
				throw new CloudsyncException("'" + propertyName + "' is not configured");
			}
		}

		if (cmd.hasOption("help") || type == null || name == null || followlinks == null || duplicate == null || !baseValid || config == null || !configValid) {

			List<String> messages = new ArrayList<String>();
			if (cmd.getOptions().length > 0) {

				messages.add("error: missing or wrong options");
				if (type == null) {
					messages.add(" You must specifiy --backup, --restore, --list or --clean");
				} else if (!baseValid) {
					messages.add(" --" + type + " <path> not valid");
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
			}
			throw new UsageException(StringUtils.join(messages, '\n'));
		}
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

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String getLimitPattern() {
		return limitPattern;
	}

	public Integer getHistory() {
		return history;
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
	public LinkType getFollowLinks() {
		return followlinks;
	}

	public DuplicateType getDuplicate() {
		return duplicate;
	}
	
	public String getProperty( String key ){
		
		return prop.getProperty(key);
	}
}
