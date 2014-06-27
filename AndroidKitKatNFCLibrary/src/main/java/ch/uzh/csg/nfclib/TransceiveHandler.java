package ch.uzh.csg.nfclib;

//TODO: javadoc
public interface TransceiveHandler {
	
	public byte[] handleMessage(byte[] message, ISendLater sendLater);
	
}
