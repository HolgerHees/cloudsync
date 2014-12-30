package cloudsync.logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;

public class LogfileHandler extends FileHandler {

	public LogfileHandler(String pattern) throws IOException, SecurityException {
		super(pattern);
	}

	@Override
	public void publish(LogRecord record) {

		if (record.getParameters() != null) return;
		
		super.publish(record);
	}
}
