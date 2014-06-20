package ch.uzh.csg.nfclib;

public interface NfcEvent {
	
	public enum Type {
		INIT_FAILED,
		INITIALIZED,
		FATAL_ERROR, //requires aborting and starting from scratch
		MESSAGE_SENT,
		MESSAGE_RECEIVED,
		MESSAGE_RETURNED,
		CONNECTION_LOST; //mainly an io error, so hold devices together to continue session
	}

	public abstract void handleMessage(Type event, Object object);

}