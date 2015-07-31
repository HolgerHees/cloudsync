package cloudsync.model;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public class RemoteStreamData
{
	private InputStream	encryptedStream;
	private InputStream	decryptedStream;

	public RemoteStreamData(InputStream encryptedStream, InputStream decryptedStream)
	{
		this.encryptedStream = encryptedStream;
		this.decryptedStream = decryptedStream;
	}

	public InputStream getDecryptedStream()
	{
		return decryptedStream;
	}
	
	public void close()
	{
		if (encryptedStream != null) IOUtils.closeQuietly(encryptedStream);
		if (decryptedStream != null) IOUtils.closeQuietly(decryptedStream);
	}
}
