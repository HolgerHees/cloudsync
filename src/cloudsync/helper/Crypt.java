package cloudsync.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.util.io.Streams;

import cloudsync.model.Item;
import cloudsync.model.ItemType;

public class Crypt {

	// private final static Logger LOGGER =
	// Logger.getLogger(Crypt.class.getName());

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

	public String decryptText(String text) throws IOException, CryptException {

		text = text.replace('_', '/');
		final byte[] data = Base64.decodeBase64(text);
		return new String(decryptData(new ByteArrayInputStream(data)));
	}

	public byte[] decryptData(final InputStream stream) throws IOException, CryptException {

		final InputStream in = PGPUtil.getDecoderStream(stream);

		try {

			final PGPObjectFactory pgpF = new PGPObjectFactory(in);
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

			PGPObjectFactory pgpFact = new PGPObjectFactory(clear);

			final PGPCompressedData cData = (PGPCompressedData) pgpFact.nextObject();

			pgpFact = new PGPObjectFactory(cData.getDataStream());

			final PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();

			return Streams.readAll(ld.getInputStream());
		} catch (final PGPException e) {

			throw new CryptException(e);
		} finally {

			in.close();
		}
	}

	public byte[] getEncryptedBinary(final File file, final Item item) throws CryptException, IOException {

		if (item.isType(ItemType.LINK)) {

			return _encryptData(Files.readSymbolicLink(file.toPath()).toString().getBytes(), file.getName(), ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);
		} else if (item.isType(ItemType.FILE)) {

			return _encryptData(Files.readAllBytes(file.toPath()), file.getName(), ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);
		}

		return null;
	}

	public String encryptText(String text) throws CryptException, IOException {

		final byte[] data = _encryptData(text.getBytes(), PGPLiteralData.CONSOLE, ENCRYPT_ALGORITHM, ENCRYPT_ARMOR);
		text = Base64.encodeBase64String(data);
		text = text.replace('/', '_');
		return text;
	}

	private byte[] _encryptData(final byte[] data, final String name, final int algorithm, final boolean armor) throws CryptException, IOException {

		final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		OutputStream out = bOut;

		try {

			final byte[] compressedData = compress(data, name, CompressionAlgorithmTags.ZIP);

			if (armor) {
				out = new ArmoredOutputStream(out);
			}

			final PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(algorithm).setSecureRandom(new SecureRandom()).setProvider("BC"));
			encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(passphrase.toCharArray()).setProvider("BC"));

			final OutputStream encOut = encGen.open(out, compressedData.length);

			encOut.write(compressedData);
			encOut.close();

			return bOut.toByteArray();

		} catch (final PGPException e) {

			throw new CryptException(e);

		} finally {

			if (armor) {
				out.close();
			}
		}
	}

	private static byte[] compress(final byte[] clearData, final String fileName, final int algorithm) throws IOException {
		final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		final PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(algorithm);
		final OutputStream cos = comData.open(bOut); // open it with the final
		// destination

		final PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

		// we want to generate compressed data. This might be a user option
		// later,
		// in which case we would pass in bOut.
		final OutputStream pOut = lData.open(cos, // the compressed output
				// stream
				PGPLiteralData.BINARY, fileName, // "filename" to store
				clearData.length, // length of clear data
				new Date() // current time
				);

		pOut.write(clearData);
		pOut.close();

		comData.close();

		return bOut.toByteArray();
	}
}
