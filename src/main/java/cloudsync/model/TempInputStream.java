package cloudsync.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TempInputStream extends FileInputStream
{
	private File	file;

	public TempInputStream(File file) throws FileNotFoundException
	{
		super(file);
		this.file = file;
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		this.file.delete();
	}

}
