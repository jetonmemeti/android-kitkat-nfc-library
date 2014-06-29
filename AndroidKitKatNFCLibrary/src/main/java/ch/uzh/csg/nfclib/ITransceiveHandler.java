package ch.uzh.csg.nfclib;

//TODO: javadoc
public interface ITransceiveHandler {
	
	public byte[] handleMessage(byte[] message, ISendLater sendLater);
	
}
