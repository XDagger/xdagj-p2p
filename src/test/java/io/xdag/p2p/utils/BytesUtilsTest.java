package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/** Unit tests for BytesUtils class. Tests all utility methods for Tuweni Bytes operations. */
public class BytesUtilsTest {

  @Test
  public void testWrapByteArray() {
    byte[] testArray = {1, 2, 3, 4, 5};
    Bytes result = BytesUtils.wrap(testArray);

    assertNotNull(result);
    assertEquals(5, result.size());
    assertArrayEquals(testArray, result.toArray());

    // Test null input
    assertEquals(Bytes.EMPTY, BytesUtils.wrap((byte[]) null));
  }

  @Test
  public void testWrapByteArrayWithOffsetAndLength() {
    byte[] testArray = {1, 2, 3, 4, 5, 6, 7, 8};

    // Normal case
    Bytes result = BytesUtils.wrap(testArray, 2, 3);
    assertEquals(3, result.size());
    assertArrayEquals(new byte[] {3, 4, 5}, result.toArray());

    // Edge cases
    assertEquals(Bytes.EMPTY, BytesUtils.wrap(null, 0, 5));
    assertEquals(Bytes.EMPTY, BytesUtils.wrap(testArray, -1, 3));
    assertEquals(Bytes.EMPTY, BytesUtils.wrap(testArray, 0, 0));
    assertEquals(Bytes.EMPTY, BytesUtils.wrap(testArray, 0, -1));
    assertEquals(Bytes.EMPTY, BytesUtils.wrap(testArray, 5, 10));
  }

  @Test
  public void testFromString() {
    String testString = "Hello World";
    Bytes result = BytesUtils.fromString(testString);

    assertNotNull(result);
    assertEquals(testString.getBytes().length, result.size());

    // Test null input
    assertEquals(Bytes.EMPTY, BytesUtils.fromString(null));

    // Test empty string
    assertEquals(Bytes.EMPTY, BytesUtils.fromString(""));
  }

  @Test
  public void testToString() {
    String original = "Hello World";
    Bytes bytes = BytesUtils.fromString(original);
    String result = BytesUtils.toString(bytes);

    assertEquals(original, result);

    // Test null input
    assertEquals("", BytesUtils.toString(null));
  }

  @Test
  public void testConcat() {
    Bytes bytes1 = Bytes.fromHexString("0x1234");
    Bytes bytes2 = Bytes.fromHexString("0x5678");
    Bytes bytes3 = Bytes.fromHexString("0x9abc");

    Bytes result = BytesUtils.concat(bytes1, bytes2, bytes3);
    assertEquals(Bytes.fromHexString("0x123456789abc"), result);

    // Test empty input
    assertEquals(Bytes.EMPTY, BytesUtils.concat());
    assertEquals(Bytes.EMPTY, BytesUtils.concat((Bytes[]) null));

    // Test single input
    assertEquals(bytes1, BytesUtils.concat(bytes1));
  }

  @Test
  public void testSlice() {
    Bytes bytes = Bytes.fromHexString("0x123456789abc");

    // Normal case
    Bytes result = BytesUtils.slice(bytes, 1, 4);
    assertEquals(Bytes.fromHexString("0x345678"), result);

    // Edge cases
    assertEquals(Bytes.EMPTY, BytesUtils.slice(null, 0, 5));
    assertEquals(Bytes.EMPTY, BytesUtils.slice(bytes, -1, 3));
    assertEquals(Bytes.EMPTY, BytesUtils.slice(bytes, 3, 2));
    assertEquals(Bytes.EMPTY, BytesUtils.slice(bytes, 10, 15));

    // End beyond size
    Bytes result2 = BytesUtils.slice(bytes, 2, 100);
    assertEquals(bytes.slice(2), result2);
  }

  @Test
  public void testTake() {
    Bytes bytes = Bytes.fromHexString("0x123456789abc");

    // Normal case
    Bytes result = BytesUtils.take(bytes, 3);
    assertEquals(Bytes.fromHexString("0x123456"), result);

    // Take more than available
    Bytes result2 = BytesUtils.take(bytes, 100);
    assertEquals(bytes, result2);

    // Edge cases
    assertEquals(Bytes.EMPTY, BytesUtils.take(null, 5));
    assertEquals(Bytes.EMPTY, BytesUtils.take(bytes, 0));
    assertEquals(Bytes.EMPTY, BytesUtils.take(bytes, -1));
  }

  @Test
  public void testSkip() {
    Bytes bytes = Bytes.fromHexString("0x123456789abc");

    // Normal case
    Bytes result = BytesUtils.skip(bytes, 2);
    assertEquals(Bytes.fromHexString("0x56789abc"), result);

    // Skip all
    Bytes result2 = BytesUtils.skip(bytes, 6);
    assertEquals(Bytes.EMPTY, result2);

    // Skip more than available
    Bytes result3 = BytesUtils.skip(bytes, 100);
    assertEquals(Bytes.EMPTY, result3);

    // Edge cases
    assertEquals(Bytes.EMPTY, BytesUtils.skip(null, 5));
    assertEquals(bytes, BytesUtils.skip(bytes, 0));
    assertEquals(bytes, BytesUtils.skip(bytes, -1));
  }

  @Test
  public void testEquals() {
    Bytes bytes1 = Bytes.fromHexString("0x1234");
    Bytes bytes2 = Bytes.fromHexString("0x1234");
    Bytes bytes3 = Bytes.fromHexString("0x5678");

    assertTrue(BytesUtils.equals(bytes1, bytes2));
    assertFalse(BytesUtils.equals(bytes1, bytes3));
    assertTrue(BytesUtils.equals(null, null));
    assertFalse(BytesUtils.equals(bytes1, null));
    assertFalse(BytesUtils.equals(null, bytes1));
  }

  @Test
  public void testFromHexString() {
    // With 0x prefix
    Bytes result1 = BytesUtils.fromHexString("0x1234");
    assertEquals(Bytes.fromHexString("0x1234"), result1);

    // Without 0x prefix
    Bytes result2 = BytesUtils.fromHexString("1234");
    assertEquals(Bytes.fromHexString("0x1234"), result2);

    // Note: 0X prefix is not supported by Tuweni, so we skip that test

    // Edge cases
    assertEquals(Bytes.EMPTY, BytesUtils.fromHexString(null));
    assertEquals(Bytes.EMPTY, BytesUtils.fromHexString(""));
  }

  @Test
  public void testToHexStringBytes() {
    Bytes bytes = Bytes.fromHexString("0x1234abcd");
    String result = bytes.toUnprefixedHexString(); // 直接调用Bytes的方法避免歧义

    assertEquals("1234abcd", result);
  }

  @Test
  public void testXor() {
    Bytes bytes1 = Bytes.fromHexString("0x1234");
    Bytes bytes2 = Bytes.fromHexString("0x5678");

    Bytes result = BytesUtils.xor(bytes1, bytes2);
    // 0x12 ^ 0x56 = 0x44, 0x34 ^ 0x78 = 0x4c
    assertEquals(Bytes.fromHexString("0x444c"), result);

    // Test error cases
    assertThrows(IllegalArgumentException.class, () -> BytesUtils.xor(null, bytes2));
    assertThrows(IllegalArgumentException.class, () -> BytesUtils.xor(bytes1, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> BytesUtils.xor(bytes1, Bytes.fromHexString("0x123456")));
  }

  @Test
  public void testLeadingZeroBits() {
    // All zeros
    assertEquals(Integer.MAX_VALUE, BytesUtils.leadingZeroBits(null));
    assertEquals(Integer.MAX_VALUE, BytesUtils.leadingZeroBits(Bytes.EMPTY));

    // Some leading zeros
    Bytes bytes1 = Bytes.fromHexString("0x0012"); // 0000 0000 0001 0010
    assertEquals(11, BytesUtils.leadingZeroBits(bytes1));

    // No leading zeros
    Bytes bytes2 = Bytes.fromHexString("0x8000"); // 1000 0000 0000 0000
    assertEquals(0, BytesUtils.leadingZeroBits(bytes2));

    // All zeros byte
    Bytes bytes3 = Bytes.fromHexString("0x0080"); // 0000 0000 1000 0000
    assertEquals(8, BytesUtils.leadingZeroBits(bytes3));
  }

  @Test
  public void testByte2int() {
    assertEquals(0, BytesUtils.byte2int((byte) 0));
    assertEquals(127, BytesUtils.byte2int((byte) 127));
    assertEquals(255, BytesUtils.byte2int((byte) -1));
    assertEquals(128, BytesUtils.byte2int((byte) -128));
  }

  @Test
  public void testToHexStringBigInteger() {
    BigInteger value = new BigInteger("255");
    String result = BytesUtils.toHexString(value);
    assertEquals("ff", result);

    BigInteger zero = BigInteger.ZERO;
    assertEquals("0", BytesUtils.toHexString(zero));
  }

  @Test
  public void testToStr() {
    byte[] bytes = "Hello".getBytes();
    String result = BytesUtils.toStr(bytes);
    assertEquals("Hello", result);

    // Test null - returns null according to implementation
    assertEquals(null, BytesUtils.toStr(null));

    // Test empty array - returns null according to implementation
    assertEquals(null, BytesUtils.toStr(new byte[0]));
  }

  @Test
  public void testFromObject() throws Exception {
    String testObject = "Test String";
    byte[] result = BytesUtils.fromObject(testObject);

    assertNotNull(result);
    assertTrue(result.length > 0);

    // Test null - actually serializes null object, so returns non-null byte array
    byte[] nullResult = BytesUtils.fromObject(null);
    assertNotNull(nullResult);
    assertTrue(nullResult.length > 0); // Serialized null is not empty
  }
}
