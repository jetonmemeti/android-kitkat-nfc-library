package ch.uzh.ch.nfclib.util;

public class Constants {
	
	/*
	 * When a remote NFC device wants to talk to your service, it sends a
	 * so-called "SELECT AID" APDU as defined in the ISO/IEC 7816-4
	 * specification.
	 */
	public static final byte[] CLA_INS_P1_P2 = { 0x00, (byte) 0xA4, 0x04, 0x00 };
	public static final byte[] AID_MBPS = { (byte) 0xF0, (byte) 0xF0, 0x07, 0x77, (byte) 0xFF, 0x55, 0x11 };
	//TODO: needed?
//	public static final byte[] AID_MBPS_RESUME = { (byte) 0xF0, (byte) 0xF0, 0x07, 0x77, (byte) 0xFF, 0x55, 0x12 };
//	public static final byte[] READ_BINARY = new byte[]{0, (byte)0xb0,0,0,1};
	
	
	
}
