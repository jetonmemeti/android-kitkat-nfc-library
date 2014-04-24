package ch.uzh.csg.nfclib;

import java.nio.ByteBuffer;
import java.util.Arrays;

import ch.uzh.csg.nfclib.util.Constants;

//TODO: javadoc
public class CommandApdu {
	
	public static byte[] getCommandApdu(long userId) {
		/*
		 * ISO 7816-4 specifies how the command APDU must look like. See
		 * http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_5_basic_organizations.aspx#chap5_3
		 */
		
		byte[] userIdBytes = getLongAsBytes(userId);
		
		//  CLA_INS_P1_P2 has by the specification 4 bytes
		byte[] temp = new byte[Constants.CLA_INS_P1_P2.length + 1 + Constants.AID_MBPS.length + userIdBytes.length + 1];
		System.arraycopy(Constants.CLA_INS_P1_P2, 0, temp, 0, Constants.CLA_INS_P1_P2.length);

		// Lc: the number of bytes present in the data field of the command APDU
		temp[Constants.CLA_INS_P1_P2.length] = (byte) Constants.AID_MBPS.length;

		// the data field
		System.arraycopy(Constants.AID_MBPS, 0, temp, Constants.CLA_INS_P1_P2.length+1, Constants.AID_MBPS.length);
		System.arraycopy(userIdBytes, 0, temp, Constants.CLA_INS_P1_P2.length+1+Constants.AID_MBPS.length, userIdBytes.length);
		
		// Le: the maximum number of bytes expected in the data field of the response APDU
		temp[temp.length - 1] = 3;
		
		return temp;
	}
	
	private static byte[] getLongAsBytes(long l) {
		return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(l).array();
	}
	
	public static long getUserId(byte[] bytes) {
		byte[] userIdBytes = Arrays.copyOfRange(bytes, Constants.CLA_INS_P1_P2.length+1+Constants.AID_MBPS.length, bytes.length-1);
		ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
		buffer.put(userIdBytes);
		buffer.flip();
		return buffer.getLong();
	}

}
