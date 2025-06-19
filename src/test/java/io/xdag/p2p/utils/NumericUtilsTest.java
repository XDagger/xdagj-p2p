/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/** Unit tests for NumericUtils class. Tests all numeric and hex string conversion utilities. */
public class NumericUtilsTest {

  @Test
  public void testCleanHexPrefix() {
    assertEquals("1234", NumericUtils.cleanHexPrefix("0x1234"));
    assertEquals("abcd", NumericUtils.cleanHexPrefix("0xabcd"));
    assertEquals("1234", NumericUtils.cleanHexPrefix("1234"));
    assertEquals("", NumericUtils.cleanHexPrefix("0x"));
    assertEquals("", NumericUtils.cleanHexPrefix(""));
    assertEquals("x1234", NumericUtils.cleanHexPrefix("x1234"));
  }

  @Test
  public void testContainsHexPrefix() {
    assertTrue(NumericUtils.containsHexPrefix("0x1234"));
    assertTrue(NumericUtils.containsHexPrefix("0xabcd"));
    assertFalse(NumericUtils.containsHexPrefix("1234"));
    assertFalse(NumericUtils.containsHexPrefix("0"));
    assertFalse(NumericUtils.containsHexPrefix(""));
    assertFalse(NumericUtils.containsHexPrefix(null));
    assertFalse(NumericUtils.containsHexPrefix("x1234"));
  }

  @Test
  public void testToBigIntFromByteArray() {
    byte[] bytes = {1, 2, 3, 4};
    BigInteger result = NumericUtils.toBigInt(bytes);
    assertEquals(new BigInteger("16909060"), result);

    byte[] emptyBytes = {};
    BigInteger emptyResult = NumericUtils.toBigInt(emptyBytes);
    assertEquals(BigInteger.ZERO, emptyResult);
  }

  @Test
  public void testToBigIntFromBytes() {
    Bytes bytes = Bytes.fromHexString("0x1234");
    BigInteger result = NumericUtils.toBigInt(bytes);
    assertEquals(new BigInteger("4660"), result);

    Bytes emptyBytes = Bytes.EMPTY;
    BigInteger emptyResult = NumericUtils.toBigInt(emptyBytes);
    assertEquals(BigInteger.ZERO, emptyResult);
  }

  @Test
  public void testToBigIntFromHexString() {
    assertEquals(new BigInteger("4660"), NumericUtils.toBigInt("0x1234"));
    assertEquals(new BigInteger("4660"), NumericUtils.toBigInt("1234"));
    assertEquals(new BigInteger("255"), NumericUtils.toBigInt("0xff"));
    assertEquals(new BigInteger("255"), NumericUtils.toBigInt("ff"));
  }

  @Test
  public void testToBigIntNoPrefix() {
    assertEquals(new BigInteger("4660"), NumericUtils.toBigIntNoPrefix("1234"));
    assertEquals(new BigInteger("255"), NumericUtils.toBigIntNoPrefix("ff"));
    assertEquals(BigInteger.ZERO, NumericUtils.toBigIntNoPrefix("0"));
  }

  @Test
  public void testToHexStringNoPrefix() {
    assertEquals("ff", NumericUtils.toHexStringNoPrefix(new BigInteger("255")));
    assertEquals("1234", NumericUtils.toHexStringNoPrefix(new BigInteger("4660")));
    assertEquals("0", NumericUtils.toHexStringNoPrefix(BigInteger.ZERO));
  }

  @Test
  public void testToHexStringNoPrefixZeroPadded() {
    assertEquals("00ff", NumericUtils.toHexStringNoPrefixZeroPadded(new BigInteger("255"), 4));
    assertEquals("1234", NumericUtils.toHexStringNoPrefixZeroPadded(new BigInteger("4660"), 4));
    assertEquals("0000", NumericUtils.toHexStringNoPrefixZeroPadded(BigInteger.ZERO, 4));

    // Test error cases
    assertThrows(
        UnsupportedOperationException.class,
        () -> NumericUtils.toHexStringNoPrefixZeroPadded(new BigInteger("65536"), 2));
    assertThrows(
        UnsupportedOperationException.class,
        () -> NumericUtils.toHexStringNoPrefixZeroPadded(new BigInteger("-1"), 4));
  }

  @Test
  public void testHexStringToByteArray() {
    byte[] expected = {0x12, 0x34};
    assertArrayEquals(expected, NumericUtils.hexStringToByteArray("0x1234"));
    assertArrayEquals(expected, NumericUtils.hexStringToByteArray("1234"));

    byte[] empty = {};
    assertArrayEquals(empty, NumericUtils.hexStringToByteArray(""));
    assertArrayEquals(empty, NumericUtils.hexStringToByteArray("0x"));
  }

  @Test
  public void testHexStringToBytes() {
    Bytes expected = Bytes.fromHexString("0x1234");
    assertEquals(expected, NumericUtils.hexStringToBytes("0x1234"));
    assertEquals(expected, NumericUtils.hexStringToBytes("1234"));

    assertEquals(Bytes.EMPTY, NumericUtils.hexStringToBytes(""));
    assertEquals(Bytes.EMPTY, NumericUtils.hexStringToBytes("0x"));
  }

  @Test
  public void testToHexStringByteArrayWithOffsetAndLength() {
    byte[] bytes = {0x12, 0x34, 0x56, 0x78};

    // With prefix
    assertEquals("0x3456", NumericUtils.toHexString(bytes, 1, 2, true));

    // Without prefix
    assertEquals("3456", NumericUtils.toHexString(bytes, 1, 2, false));

    // Null or empty array
    assertEquals("0x", NumericUtils.toHexString(null, 0, 0, true));
    assertEquals("", NumericUtils.toHexString(null, 0, 0, false));
    assertEquals("0x", NumericUtils.toHexString(new byte[0], 0, 0, true));
  }

  @Test
  public void testToHexStringByteArray() {
    byte[] bytes = {0x12, 0x34, 0x56, 0x78};
    assertEquals("0x12345678", NumericUtils.toHexString(bytes));

    // Null or empty array
    assertEquals("0x", NumericUtils.toHexString((byte[]) null));
    assertEquals("0x", NumericUtils.toHexString(new byte[0]));
  }

  @Test
  public void testToHexStringBytes() {
    Bytes bytes = Bytes.fromHexString("0x12345678");
    assertEquals("0x12345678", NumericUtils.toHexString(bytes));

    // Null or empty Bytes
    assertEquals("0x", NumericUtils.toHexString((Bytes) null));
    assertEquals("0x", NumericUtils.toHexString(Bytes.EMPTY));
  }

  @Test
  public void testEdgeCases() {
    // Test very large numbers
    BigInteger large = new BigInteger("123456789012345678901234567890");
    String largeHex = NumericUtils.toHexStringNoPrefix(large);
    assertEquals(large, NumericUtils.toBigIntNoPrefix(largeHex));

    // Test single digit
    assertEquals("a", NumericUtils.toHexStringNoPrefix(new BigInteger("10")));
    assertEquals(new BigInteger("10"), NumericUtils.toBigInt("a"));
  }

  @Test
  public void testRoundTripConversions() {
    // Test round trip: BigInteger -> hex -> BigInteger
    BigInteger original = new BigInteger("123456789");
    String hex = NumericUtils.toHexStringNoPrefix(original);
    BigInteger restored = NumericUtils.toBigIntNoPrefix(hex);
    assertEquals(original, restored);

    // Test round trip: bytes -> hex -> bytes
    byte[] originalBytes = {0x12, 0x34, 0x56, 0x78, (byte) 0xab, (byte) 0xcd};
    String hexFromBytes = NumericUtils.toHexString(originalBytes);
    byte[] restoredBytes = NumericUtils.hexStringToByteArray(hexFromBytes);
    assertArrayEquals(originalBytes, restoredBytes);
  }
}
