package cloudsync.helper;

public class CloudsyncException extends Exception {

	private static final long serialVersionUID = 5775239254896263691L;

	public CloudsyncException(final String message) {
		super(message);
	}

	public CloudsyncException(final String message, final Exception e) {
		super(message, e);
	}
}
