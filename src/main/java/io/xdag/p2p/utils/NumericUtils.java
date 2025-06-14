/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
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

import java.math.BigInteger;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

/**
 * Utility class for numeric and hex string conversions Uses Apache Tuweni Bytes for efficient byte
 * operations
 */
public class NumericUtils {

  private static final String HEX_PREFIX = "0x";

  private NumericUtils() {}

  /** Removes "0x" prefix from hex string if present */
  public static String cleanHexPrefix(String input) {
    if (containsHexPrefix(input)) {
      return input.substring(2);
    } else {
      return input;
    }
  }

  /** Checks if string starts with "0x" prefix */
  public static boolean containsHexPrefix(String input) {
    return !StringUtils.isEmpty(input)
        && input.length() > 1
        && input.charAt(0) == '0'
        && input.charAt(1) == 'x';
  }

  /** Converts byte array to BigInteger */
  public static BigInteger toBigInt(byte[] value) {
    return new BigInteger(1, value);
  }

  /** Converts Tuweni Bytes to BigInteger */
  public static BigInteger toBigInt(Bytes value) {
    return value.toUnsignedBigInteger();
  }

  /** Converts hex string to BigInteger, handling "0x" prefix */
  public static BigInteger toBigInt(String hexValue) {
    String cleanValue = cleanHexPrefix(hexValue);
    return toBigIntNoPrefix(cleanValue);
  }

  /** Converts hex string without prefix to BigInteger */
  public static BigInteger toBigIntNoPrefix(String hexValue) {
    return new BigInteger(hexValue, 16);
  }

  /** Converts BigInteger to hex string without "0x" prefix */
  public static String toHexStringNoPrefix(BigInteger value) {
    return value.toString(16);
  }

  /** Converts BigInteger to zero-padded hex string without prefix */
  public static String toHexStringNoPrefixZeroPadded(BigInteger value, int size) {
    return toHexStringZeroPadded(value, size, false);
  }

  /** Helper method to convert BigInteger to zero-padded hex string */
  private static String toHexStringZeroPadded(BigInteger value, int size, boolean withPrefix) {
    String result = toHexStringNoPrefix(value);

    int length = result.length();
    if (length > size) {
      throw new UnsupportedOperationException("Value " + result + "is larger then length " + size);
    } else if (value.signum() < 0) {
      throw new UnsupportedOperationException("Value cannot be negative");
    }

    if (length < size) {
      result = StringUtils.repeat('0', size - length) + result;
    }

    if (withPrefix) {
      return HEX_PREFIX + result;
    } else {
      return result;
    }
  }

  /** Converts hex string to byte array using Tuweni Bytes */
  public static byte[] hexStringToByteArray(String input) {
    String cleanInput = cleanHexPrefix(input);
    if (cleanInput.isEmpty()) {
      return new byte[] {};
    }
    return Bytes.fromHexString(cleanInput).toArray();
  }

  /** Converts hex string to Tuweni Bytes */
  public static Bytes hexStringToBytes(String input) {
    String cleanInput = cleanHexPrefix(input);
    if (cleanInput.isEmpty()) {
      return Bytes.EMPTY;
    }
    return Bytes.fromHexString(cleanInput);
  }

  /** Converts byte array to hex string with offset and length */
  public static String toHexString(byte[] input, int offset, int length, boolean withPrefix) {
    if (input == null || input.length == 0) {
      return withPrefix ? HEX_PREFIX : "";
    }
    Bytes bytes = Bytes.wrap(input, offset, length);
    String hex = bytes.toHexString().substring(2); // Remove 0x prefix from Tuweni
    return withPrefix ? HEX_PREFIX + hex : hex;
  }

  /** Converts byte array to hex string with "0x" prefix using Tuweni */
  public static String toHexString(byte[] input) {
    if (input == null || input.length == 0) {
      return HEX_PREFIX;
    }
    return Bytes.wrap(input).toHexString();
  }

  /** Converts Tuweni Bytes to hex string with "0x" prefix */
  public static String toHexString(Bytes input) {
    if (input == null || input.isEmpty()) {
      return HEX_PREFIX;
    }
    return input.toHexString();
  }
}
