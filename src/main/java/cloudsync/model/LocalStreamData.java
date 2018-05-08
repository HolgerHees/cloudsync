package cloudsync.model;

import java.io.InputStream;

public class LocalStreamData
{
	private final InputStream	data;
	private final long			length;

	public LocalStreamData(InputStream data, long length)
	{
		this.data = data;
		this.length = length;
	}

	public InputStream getStream()
	{
		return data;
	}

	public long getLength()
	{
		return length;
	}
}
