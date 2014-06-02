package ch.uzh.csg.nfclib;

//TODO: javadoc
public enum NfcEvent {
	INIT_FAILED,
	INITIALIZED,
	FATAL_ERROR, //requires aborting and starting from scratch
	MESSAGE_SENT,
	MESSAGE_RECEIVED,
	MESSAGE_RETURNED,
	CONNECTION_LOST; //mainly an io error, so hold devices together to continue session
}
