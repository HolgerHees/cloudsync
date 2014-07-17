package cloudsync.logging;

import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogfileFormatter extends Formatter {

	final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	
	@Override
	public String format(LogRecord record) {
		
		return sdf.format(record.getMillis()) + " " + record.getMessage() + "\n";
	}

}
