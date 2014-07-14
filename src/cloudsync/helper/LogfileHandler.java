package cloudsync.helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;

public class LogfileHandler extends ConsoleHandler {

	private boolean keepCurrentLine;
	final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	public void publish(LogRecord record) {

		if (record.getParameters() == null) {
			if (keepCurrentLine) {
				keepCurrentLine = false;
				System.out.print("\r");
			}
			System.out.println(sdf.format(new Date()) + " " + record.getMessage());

		} else {

			keepCurrentLine = true;
			System.out.print(record.getMessage());
		}
	}
}
