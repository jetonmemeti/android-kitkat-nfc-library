package ch.uzh.csg.nfclib;

public interface NfcEvent {
	
	public enum Type {
		INIT_FAILED, //exit condition
		INITIALIZED, //may continue
		FATAL_ERROR, //exit condition
		MESSAGE_SENT, //TODO: same as message_received -> remove!
		MESSAGE_RECEIVED, //exit condition
		CONNECTION_LOST, //exit condition
	}

	public abstract void handleMessage(Type event, Object object);

}