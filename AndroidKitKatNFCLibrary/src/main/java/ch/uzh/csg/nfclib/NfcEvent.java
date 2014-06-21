package ch.uzh.csg.nfclib;

public interface NfcEvent {
	
	public enum Type {
		INIT_FAILED,
		INITIALIZED,
		FATAL_ERROR, //requires aborting and starting from scratch
		MESSAGE_SENT,
		MESSAGE_RECEIVED,
		CONNECTION_LOST, //mainly an io error, so hold devices together to continue session
		MESSAGE_RECEIVED_PARTIAL, 
		INITIALIZED_HCE, 
		MESSAGE_RECEIVED_HCE, 
		MESSAGE_SENT_HCE; 
	}

	public abstract void handleMessage(Type event, Object object);

}