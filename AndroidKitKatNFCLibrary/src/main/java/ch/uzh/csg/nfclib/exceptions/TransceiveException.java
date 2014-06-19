package ch.uzh.csg.nfclib.exceptions;

import ch.uzh.csg.nfclib.NfcEvent;

//TODO: javadoc
public class TransceiveException extends Exception {
	private static final long serialVersionUID = 9093360699705265326L;
	
	private NfcEvent nfcEvent;

	public TransceiveException(NfcEvent event, String description) {
		super(description);
		this.nfcEvent = event;
	}
	
	public NfcEvent getNfcEvent() {
		return nfcEvent;
	}
	
}
