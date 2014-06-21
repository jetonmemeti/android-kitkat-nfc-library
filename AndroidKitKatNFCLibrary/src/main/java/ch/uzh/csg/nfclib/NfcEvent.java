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
		INITIALIZED_HCE, //TODO thomas: remove
		MESSAGE_RECEIVED_HCE, //TODO thomas: remove
		MESSAGE_SENT_HCE, //TODO thomas: remove
		INIT_RETRY_FAILED; //TODO thomas:  why distinguish between this and INIT_FAILED?
	}

	public abstract void handleMessage(Type event, Object object);

}