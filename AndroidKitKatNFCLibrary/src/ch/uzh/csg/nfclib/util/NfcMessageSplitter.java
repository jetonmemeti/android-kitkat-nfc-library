package ch.uzh.csg.nfclib.util;

import java.util.ArrayList;
import java.util.Arrays;

import ch.uzh.csg.nfclib.messages.NfcMessage;

/**
 * Is responsible for NfcMessage (or byte buffer) fragmentation in order to not
 * exceed the maximum allowed message length by the underlying NFC technology.
 * 
 * @author Jeton Memeti
 * 
 */
public class NfcMessageSplitter {

	private int payloadLength;
	
	/**
	 * Returns a new NfcMessageSplitter to handle the fragmentation of
	 * NfcMessages.
	 * 
	 * @param isoDepMaxTransceiveLength
	 *            the maximum number of bytes which can be send at once by the
	 *            underlying NFC technology
	 */
	public NfcMessageSplitter(int isoDepMaxTransceiveLength) {
		payloadLength = isoDepMaxTransceiveLength - NfcMessage.HEADER_LENGTH;
	}

	/**
	 * Fragments the payload into a number of NfcMessages so that no NfcMessage
	 * exceeds the isoDepMaxTransceiveLength. If no fragmentation is needed
	 * (because the payload does not reach the threshold), then a list
	 * containing only one NfcMessage is returned.
	 * 
	 * The sequence number of the NfcMessages is not set here (all messages in
	 * the list have the sequence number 0)! It must be set appropriately
	 * elsewhere.
	 * 
	 * @param payload
	 *            the whole message or byte array to be send by NFC
	 * @return an ArrayList of NfcMessages containing the fragmented payload
	 */
	public ArrayList<NfcMessage> getFragments(byte[] payload) {
		ArrayList<NfcMessage> list = new ArrayList<NfcMessage>();
		
		int totalNofMessagesToSend = getTotalNofFragmentsToSend(payload.length);
		
		for (int i=0; i<totalNofMessagesToSend; i++) {
			int start = i*payloadLength;
			int end = (start+payloadLength > payload.length) ? payload.length : (start+payloadLength);
			byte[] temp = Arrays.copyOfRange(payload, start, end);
			byte status = (i < (totalNofMessagesToSend-1)) ? NfcMessage.HAS_MORE_FRAGMENTS : NfcMessage.DEFAULT;
			// The sequence number which is added here is not important, since
			// it will be overwritten anyway!
			list.add(new NfcMessage(status, (byte) 0x00, temp));
		}
		
		return list;
	}
	
	/**
	 * Returns the number of fragments the whole message needs to be split into
	 * (taking into account protocol headers etc.).
	 * 
	 * @param payloadLength
	 *            the length of the whole message or byte array which should be
	 *            send by NFC
	 * @return
	 */
	private int getTotalNofFragmentsToSend(int payloadLength) {
		int nofMessagesToSend = payloadLength / this.payloadLength;
		// round since int division truncates the result
		if (this.payloadLength * nofMessagesToSend < payloadLength)
			nofMessagesToSend++;
		
		return nofMessagesToSend;
	}
	
}
