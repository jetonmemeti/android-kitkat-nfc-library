package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

//TODO: javadoc
/*
 * composition for test purposes! mock the final isodep, which is 
 * not possible with mockito (IsoDep has final modifier), powermock not working on android!
 */
public class CustomIsoDep {

	private IsoDep isoDep;

	public void connect() throws IOException {
		isoDep.connect();
	}

	public byte[] transceive(byte[] bytes) throws IOException {
		return isoDep.transceive(bytes);
	}

	public boolean isConnected() {
		return isoDep.isConnected();
	}

	public int getMaxTransceiveLength() {
		return isoDep.getMaxTransceiveLength();
	}

	public void close() throws IOException {
		isoDep.close();
	}

	public void init(Tag tag) {
		isoDep = IsoDep.get(tag);
	}

}
