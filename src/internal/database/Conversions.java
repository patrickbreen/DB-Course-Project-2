package internal.database;

import java.nio.ByteBuffer;

/*******************************************************************************
 * @file  Conversions.java
 *
 * @author   John Miller
 *
 * @see http://snippets.dzone.com/posts/show/93
 */

/*******************************************************************************
 * This class provides methods for converting Java's primitive data types into
 * byte arrays.
 */
public class Conversions {
	/***************************************************************************
	 * Convert short into a byte array.
	 * 
	 * @param value
	 *            the short value to convert
	 * @return a corresponding byte array
	 */
	public static byte[] short2ByteArray(short value) {
		return ByteBuffer.allocate(2).putShort(value).array();
	} // short2ByteArray
	
	/***************************************************************************
	 * Convert a byte array to short value
	 * @param array The array which contains the byte conversion of the short
	 * @return The short value of that array
	 */
	public static short byteArray2Short(byte[] array){
		return ByteBuffer.wrap(array).getShort();
	}

	/***************************************************************************
	 * Convert int into a byte array.
	 * 
	 * @param value
	 *            the int value to convert
	 * @return a corresponding byte array
	 */
	public static byte[] int2ByteArray(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	} // int2ByteArray

	/***************************************************************************
	 * Convert a byte array to int value
	 * @param array The array which contains the byte conversion of the int
	 * @return The int value of that array
	 */
	public static int byteArray2Int(byte[] array){
		return ByteBuffer.wrap(array).getInt();
	}
	
	/***************************************************************************
	 * Convert long into a byte array.
	 * 
	 * @param value
	 *            the long value to convert
	 * @return a corresponding byte array
	 */
	public static byte[] long2ByteArray(long value) {
		return ByteBuffer.allocate(8).putLong(value).array();
	} // long2ByteArray
	
	/***************************************************************************
	 * Convert a byte array to long value
	 * @param array The array which contains the byte conversion of the long
	 * @return The long value of that array
	 */
	public static long byteArray2Long(byte[] array){
		return ByteBuffer.wrap(array).getLong();
	}

	/***************************************************************************
	 * Convert float into a byte array.
	 * 
	 * @param value
	 *            the float value to convert
	 * @return a corresponding byte array
	 */
	public static byte[] float2ByteArray(float value) {
		return ByteBuffer.allocate(4).putFloat(value).array();
	} // float2ByteArray

	
	/***************************************************************************
	 * Convert a byte array to float value
	 * @param array The array which contains the byte conversion of the float
	 * @return The float value of that array
	 */
	public static float byteArray2Float(byte[] array){
		return ByteBuffer.wrap(array).getFloat();
	}
	
	/***************************************************************************
	 * Convert double into a byte array.
	 * 
	 * @param value
	 *            the double value to convert
	 * @return a corresponding byte array
	 */
	public static byte[] double2ByteArray(double value) {
		return ByteBuffer.allocate(8).putDouble(value).array();
	} // double2ByteArray

	/***************************************************************************
	 * Convert a byte array to double value
	 * @param array The array which contains the byte conversion of the double
	 * @return The double value of that array
	 */
	public static double byteArray2Double(byte[] array){
		return ByteBuffer.wrap(array).getDouble();
	}
	
} // Conversions
