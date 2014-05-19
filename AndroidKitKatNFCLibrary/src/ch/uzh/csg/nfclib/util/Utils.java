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

	/**
	 * Returns a long from a given byte array.
	 */
	public static long getBytesAsLong(byte[] b) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
		buffer.put(b);
		buffer.flip();
		return buffer.getLong();
	}
	
	/**
	 * Returns a short as a byte array.
	 */
	public static byte[] getShortAsBytes(short s) {
		return ByteBuffer.allocate(Short.SIZE / Byte.SIZE).putShort(s).array();
	}
	
	/**
	 * Returns a short from a given byte array.
	 */
	public static short getBytesAsShort(byte[] b) {
		ByteBuffer buffer = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
		buffer.put(b);
		buffer.flip();
		return buffer.getShort();
	}
	
}
