package cloudsync.logging;

import java.text.SimpleDateFormat;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;

import org.apache.commons.lang3.StringUtils;

public class LogconsoleHandler extends ConsoleHandler {

	private boolean keepCurrentLine;
	private int sizeCurrentLine;
	final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	@Override
	public void publish(LogRecord record) {

		if (record.getParameters() == null) {
			if (keepCurrentLine) {
				keepCurrentLine = false;
				sizeCurrentLine = 0;
				System.out.print("\r");
			}
			System.out.println(LogfileFormatter.formatRecord(record, sdf));

		} else {
			
			int _sizeCurrentLine = record.getMessage().length();

			if( _sizeCurrentLine < sizeCurrentLine ) System.out.print("\r" + StringUtils.repeat(' ', sizeCurrentLine));

			sizeCurrentLine = _sizeCurrentLine;
			keepCurrentLine = true;
			System.out.print(record.getMessage());
		}
	}
}
