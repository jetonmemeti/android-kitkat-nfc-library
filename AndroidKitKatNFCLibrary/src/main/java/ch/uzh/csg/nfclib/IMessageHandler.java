package ch.uzh.csg.nfclib;

//TODO: javadoc
public interface IMessageHandler {
	
	//blocking! avoid long calculations
	public byte[] handleMessage(byte[] message, SendLater sendLater2);
	
}
