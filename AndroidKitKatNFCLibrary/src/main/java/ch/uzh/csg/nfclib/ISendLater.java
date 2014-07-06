package ch.uzh.csg.nfclib;

/**
 * The implementation of this interface must send the provided message over NFC
 * as soon as it arrives. In the meanwhile, polling is needed.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public interface ISendLater {
	
	/**
	 * Send the byte array over NFC to the counterpart.
	 * 
	 * @param bytes
	 *            the serialized message to return
	 */
	public void sendLater(byte[] bytes);

}
