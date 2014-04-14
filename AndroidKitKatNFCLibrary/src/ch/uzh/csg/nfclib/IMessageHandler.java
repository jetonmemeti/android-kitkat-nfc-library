package ch.uzh.csg.nfclib;

public interface IMessageHandler {
	
	//blocking! avoid long calculations
	public byte[] handleMessage(byte[] message);
	
}
