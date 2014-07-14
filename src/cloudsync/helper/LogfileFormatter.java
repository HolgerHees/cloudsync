package cloudsync.helper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogfileFormatter extends Formatter {

	final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	@Override
	public String format(final LogRecord r) {
		final StringBuilder sb = new StringBuilder();
		sb.append(sdf.format(new Date())).append(" ").append(formatMessage(r)).append(System.getProperty("line.separator"));
		if (null != r.getThrown()) {
			sb.append("Throwable occurred: "); //$NON-NLS-1$
			final Throwable t = r.getThrown();
			PrintWriter pw = null;
			try {
				final StringWriter sw = new StringWriter();
				pw = new PrintWriter(sw);
				t.printStackTrace(pw);
				sb.append(sw.toString());
			} finally {
				if (pw != null) {
					try {
						pw.close();
					} catch (final Exception e) {
						// ignore
					}
				}
			}
		}
		return sb.toString();
	}
}