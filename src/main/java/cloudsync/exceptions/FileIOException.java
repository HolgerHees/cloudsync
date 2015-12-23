package cloudsync.exceptions;

public class FileIOException extends Exception
{
	private static final long	serialVersionUID	= 5775239254896263691L;

	public FileIOException(final String message)
	{
		super(message);
	}

	public FileIOException(final String message, final Exception e)
	{
		super(message, e);
	}
}
