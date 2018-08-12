package cloudsync.exceptions;

public class DataException extends Exception
{
	private static final long	serialVersionUID	= 5775239254896263691L;

	public DataException(final String message)
	{
		super(message);
	}

	public DataException(final String message, final Exception e)
	{
		super(message, e);
	}
}
