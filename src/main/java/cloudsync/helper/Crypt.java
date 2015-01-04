package cloudsync.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.util.io.Streams;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.model.Item;
import cloudsync.model.StreamData;
import cloudsync.model.TempInputStream;

public class Crypt {

	// private final static Logger LOGGER =
	// Logger.getLogger(Crypt.class.getName());

	private final static Logger LOGGER = Logger.getLogger(Crypt.class.getName());

	private static final int BUFFER_SIZE = 1 << 16;
	
	private static int ENCRYPT_ALGORITHM = PGPEncryptedDataGenerator.AES_256;
	private static boolean ENCRYPT_ARMOR = false;

	private final String passphrase;

	public Crypt(final String passphrase) throws CloudsyncException {

		this.passphrase = passphrase;

		int allowedKeyLength = 0;
		try {
			allowedKeyLength = Cipher.getMaxAllowedKeyLength("AES");
		} catch (final NoSuchAlgorithmException e) {
			// e.printStackTrace();
		}
		if (allowedKeyLength < Integer.MAX_VALUE) {
			throw new CloudsyncException("Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files are not installed");
		}

		Security.addProvider(new BouncyCastleProvider());
	}

	public String decryptText(String text) throws CloudsyncException {

		text = text.replace('_', '/');
		final byte[] data = Base64.decodeBase64(text);
		try {
			return new String( Streams.readAll( decryptData(new ByteArrayInputStream(data)) ));
		} catch (IOException e) {
			throw new CloudsyncException("can't encrypt data", e);
		}
	}

	public InputStream decryptData(final InputStream stream) throws CloudsyncException {

		InputStream in = null;

		try {

			in = PGPUtil.getDecoderStream(stream);

			final PGPObjectFactory pgpF = new JcaPGPObjectFactory(in);
			PGPEncryptedDataList enc;
			final Object o = pgpF.nextObject();

			// the first object might be a PGP marker packet.
			if (o instanceof PGPEncryptedDataList) {
				enc = (PGPEncryptedDataList) o;
			} else {
				enc = (PGPEncryptedDataList) pgpF.nextObject();
			}

			final PGPPBEEncryptedData pbe = (PGPPBEEncryptedData) enc.get(0);

			final InputStream clear = pbe.getDataStream(new JcePBEDataDecryptorFactoryBuilder(new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC").build()).setProvider("BC").build(
					passphrase.toCharArray()));

			PGPObjectFactory pgpFact = new JcaPGPObjectFactory(clear);

			final PGPCompressedData cData = (PGPCompressedData) pgpFact.nextObject();

			pgpFact = new JcaPGPObjectFactory(cData.getDataStream());
			
			final PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();
			
			return ld.getInputStream();

		} catch (Exception e) {
			
			throw new CloudsyncException("can't encrypt data", e);
		}
	}

	public StreamData encryptedBinary(final String name, final StreamData data, final Item item) throws CloudsyncException {
		
		// 128MB
		if( data.getLength() < 134217728 ){

			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			_encryptData(output, data.getStream(), data.getLength(), name, ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);
			
			final byte[] bytes = output.toByteArray();
			
			return new StreamData( new ByteArrayInputStream(bytes), bytes.length );
		}
		else{
			

			try {
				
				File temp = File.createTempFile("encrypted", ".pgp");
				temp.deleteOnExit();
				final FileOutputStream output = new FileOutputStream(temp);
				_encryptData(output, data.getStream(), data.getLength(), name, ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);

				return new StreamData( new TempInputStream(temp), temp.length() );

			} catch (IOException e) {
				
				throw new CloudsyncException("can't encrypt data", e);
			} 
		}
	}

	public String encryptText(String text) throws CloudsyncException {

		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final byte[] bytes = text.getBytes();
		_encryptData(output, new ByteArrayInputStream( bytes ), bytes.length, PGPLiteralData.CONSOLE, ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);

		text = Base64.encodeBase64String(output.toByteArray());
		text = text.replace('/', '_');
		return text;
	}

	private void _encryptData(final OutputStream output, final InputStream input, final long length, final String name, final int algorithm, final boolean armor) throws CloudsyncException {

		OutputStream out = output;

		try {

			if (armor) out = new ArmoredOutputStream(out);

			final PGPEncryptedDataGenerator encryptedDataGenerator = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(algorithm).setSecureRandom(new SecureRandom()).setProvider("BC"));
			encryptedDataGenerator.addMethod(new JcePBEKeyEncryptionMethodGenerator(passphrase.toCharArray()).setProvider("BC"));
			final OutputStream encryptedData = encryptedDataGenerator.open(out, new byte[BUFFER_SIZE]);
			
			PGPCompressedDataGenerator compressedDataGenerator = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
            OutputStream compressedOut = compressedDataGenerator.open(encryptedData);

            final PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();
    		final OutputStream literalOut = literalDataGenerator.open(compressedOut, PGPLiteralData.BINARY, name, new Date(), new byte[BUFFER_SIZE] );
    		
    		byte[] buffer = new byte[BUFFER_SIZE];
			int len = 0;
			
    		if( output instanceof FileOutputStream )
    		{
	    		double current = 0;
	    		DecimalFormat df = new DecimalFormat("00");
	    		
    			while ((len = input.read(buffer)) != -1)
    			{
    				literalOut.write(buffer, 0, len);
	                current += len;
					String msg = "\r  " + df.format(Math.ceil(current*100/length)) + "% (" + convertToKB(current) + " of " + convertToKB(length) + " kb) encrypted";
					LOGGER.log(Level.FINEST, msg, true);
				}
    		}
    		else
    		{
    			while ((len = input.read(buffer)) != -1)
    			{
    				literalOut.write(buffer, 0, len);
				}
    		}
    		
            input.close();
            literalOut.close();
            literalDataGenerator.close();

            compressedOut.close();
            compressedDataGenerator.close();
            encryptedData.close();
            encryptedDataGenerator.close();

		} catch (Exception e) {
			throw new CloudsyncException("can't encrypt data", e);
			
		} finally {

			if (armor) {
				try {
					out.close();
				} catch (IOException e) {
					throw new CloudsyncException("can't encrypt data", e);
				}
			}
		}
	}
	
	private long convertToKB(double size) {

		return (long) Math.ceil(size / 1024);
	}
}
