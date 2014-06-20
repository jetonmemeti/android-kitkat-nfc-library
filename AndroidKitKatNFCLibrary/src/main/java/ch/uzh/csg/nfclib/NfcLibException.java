package ch.uzh.csg.nfclib;


//TODO: javadoc
public class NfcLibException extends Exception {

	private static final long serialVersionUID = -8780373763513187959L;

	private NfcEvent.Type nfcEvent = null;

	public NfcLibException(Throwable t) {
		super(t);
	}

	public NfcLibException(String msg) {
		super(msg);
	}

	public NfcLibException(NfcEvent.Type event, String msg) {
		super(msg);
		this.nfcEvent = event;
	}

	public NfcEvent.Type nfcEvent() {
		return nfcEvent;
	}
}
