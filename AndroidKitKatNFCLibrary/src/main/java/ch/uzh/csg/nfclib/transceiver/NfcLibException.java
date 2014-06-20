package ch.uzh.csg.nfclib.transceiver;

import ch.uzh.csg.nfclib.NfcEvent;

//TODO: javadoc
public class NfcLibException extends Exception {
	
	private static final long serialVersionUID = -8780373763513187959L;
	
	private NfcEvent nfcEvent = null;
	
	public NfcLibException(String msg) {
		super (msg);
	}
	
	public NfcLibException(NfcEvent event, String msg) {
		super(msg);
		this.nfcEvent = event;
	}
	
	public NfcEvent nfcEvent() {
		return nfcEvent;
	}
}
