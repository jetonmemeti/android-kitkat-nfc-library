package ch.uzh.csg.nfclib.util;

import java.nio.ByteBuffer;

/**
 * This is a class for miscellaneous functions.
 * 
 * @author Jeton Memeti
 * 
 */
public class Utils {
	
	/**
	 * Returns a long as a byte array.
	 */
	public static byte[] getLongAsBytes(long l) {
		return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(l).array();
	}

}
