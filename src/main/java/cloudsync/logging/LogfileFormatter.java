package cloudsync.logging;

import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogfileFormatter extends Formatter {

	final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	@Override
	public String format(LogRecord record) {

		return formatRecord(record, sdf) + "\n";
	}

	public static String formatRecord(LogRecord record, SimpleDateFormat sdf) {

		return "[" + sdf.format(record.getMillis()) + "] - " + record.getLevel().getName() + " - " + record.getMessage();
	}
}
