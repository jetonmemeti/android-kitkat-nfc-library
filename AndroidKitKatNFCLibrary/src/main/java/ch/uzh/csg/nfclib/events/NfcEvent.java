package ch.uzh.csg.nfclib.events;

/**
 * This represents all event types, which are or can be send during a NFC
 * communication.
 * 
 * Based on the event, additional data may be provided:
 * INIT_FAILED --> no data
 * INITIALIZED --> the user id of the communication partner
 * FATAL_ERROR --> the error code
 * MESSAGE_RECEIVED --> the received serialized message
 * CONNECTION_LOST --> no data
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public enum NfcEvent {
	INIT_FAILED, //exit condition
	INITIALIZED, //may continue
	FATAL_ERROR, //exit condition
	MESSAGE_RECEIVED, //exit condition
	CONNECTION_LOST; //exit condition
	
}
