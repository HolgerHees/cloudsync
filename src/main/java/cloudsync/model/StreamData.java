package cloudsync.model;

import java.io.InputStream;

public class StreamData {
	private InputStream data;
	private long length;
	
	public StreamData(InputStream data, long length){
		this.data = data;
		this.length = length;
	}
	
	public InputStream getStream() {
		return data;
	}

	public long getLength() {
		return length;
	}
}
