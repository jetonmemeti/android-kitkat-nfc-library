package ch.uzh.csg.nfclib;

import ch.uzh.csg.nfclib.CustomHostApduService2.SendLater;

//TODO: javadoc
public interface TransceiveHandler {
	
	public byte[] handleMessage(byte[] message, SendLater sendLater);
	
}
