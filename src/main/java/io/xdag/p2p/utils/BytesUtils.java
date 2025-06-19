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

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Utility class for Tuweni Bytes operations across the P2P library. This centralizes all byte
 * manipulation using Apache Tuweni for consistency and performance.
 */
@Slf4j
public class BytesUtils {

  /** Private constructor to prevent instantiation of utility class. */
  private BytesUtils() {
    // Utility class
  }

  /**
   * Wrap byte array into Tuweni Bytes object.
   *
   * @param bytes the byte array to wrap
   * @return Tuweni Bytes object
   */
  public static Bytes wrap(byte[] bytes) {
    return bytes == null ? Bytes.EMPTY : Bytes.wrap(bytes);
  }

  /**
   * Wrap a portion of byte array as Tuweni Bytes
   *
   * @param bytes the source byte array to wrap
   * @param offset starting position in the array
   * @param length number of bytes to wrap
   * @return Tuweni Bytes object containing the specified portion
   */
  public static Bytes wrap(byte[] bytes, int offset, int length) {
    if (bytes == null || offset < 0 || length <= 0 || offset + length > bytes.length) {
      return Bytes.EMPTY;
    }
    return Bytes.wrap(bytes, offset, length);
  }

  /**
   * Convert string to Tuweni Bytes using UTF-8 encoding
   *
   * @param str the string to convert
   * @return Tuweni Bytes object containing the UTF-8 encoded string
   */
  public static Bytes fromString(String str) {
    return str == null ? Bytes.EMPTY : Bytes.wrap(str.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Convert Tuweni Bytes to string using UTF-8 encoding
   *
   * @param bytes the Tuweni Bytes to convert
   * @return UTF-8 string representation of the bytes
   */
  public static String toString(Bytes bytes) {
    return bytes == null ? "" : new String(bytes.toArray(), StandardCharsets.UTF_8);
  }

  /**
   * Concatenate multiple Bytes into one
   *
   * @param bytesArray variable number of Bytes objects to concatenate
   * @return concatenated Bytes object
   */
  public static Bytes concat(Bytes... bytesArray) {
    if (bytesArray == null || bytesArray.length == 0) {
      return Bytes.EMPTY;
    }
    return Bytes.concatenate(bytesArray);
  }

  /**
   * Slice bytes from start to end (exclusive)
   *
   * @param bytes the source Bytes to slice from
   * @param start starting index (inclusive)
   * @param end ending index (exclusive)
   * @return sliced Bytes object
   */
  public static Bytes slice(Bytes bytes, int start, int end) {
    if (bytes == null || start < 0 || end <= start || start >= bytes.size()) {
      return Bytes.EMPTY;
    }
    int actualEnd = Math.min(end, bytes.size());
    return bytes.slice(start, actualEnd - start);
  }

  /**
   * Take first n bytes
   *
   * @param bytes the source Bytes object
   * @param n number of bytes to take from the beginning
   * @return Bytes object containing the first n bytes
   */
  public static Bytes take(Bytes bytes, int n) {
    if (bytes == null || n <= 0) {
      return Bytes.EMPTY;
    }
    return bytes.slice(0, Math.min(n, bytes.size()));
  }

  /**
   * Skip first n bytes and return the rest
   *
   * @param bytes the source Bytes object
   * @param n number of bytes to skip from the beginning
   * @return Bytes object containing the remaining bytes after skipping
   */
  public static Bytes skip(Bytes bytes, int n) {
    if (bytes == null || n <= 0) {
      return bytes == null ? Bytes.EMPTY : bytes;
    }
    if (n >= bytes.size()) {
      return Bytes.EMPTY;
    }
    return bytes.slice(n);
  }

  /**
   * Check if two Bytes are equal
   *
   * @param a first Bytes object to compare
   * @param b second Bytes object to compare
   * @return true if both Bytes objects are equal, false otherwise
   */
  public static boolean equals(Bytes a, Bytes b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }

  /**
   * Convert hex string to Tuweni Bytes
   *
   * @param hex the hex string to convert (with or without 0x prefix)
   * @return Tuweni Bytes object representing the hex string
   */
  public static Bytes fromHexString(String hex) {
    if (hex == null || hex.isEmpty()) {
      return Bytes.EMPTY;
    }
    return hex.startsWith("0x") || hex.startsWith("0X")
        ? Bytes.fromHexString(hex)
        : Bytes.fromHexString("0x" + hex);
  }

  /**
   * Convert Tuweni Bytes to hex string without 0x prefix
   *
   * @param bytes the Bytes object to convert
   * @return hex string representation without 0x prefix
   */
  public static String toHexString(Bytes bytes) {
    return bytes == null ? "" : bytes.toUnprefixedHexString();
  }

  /**
   * XOR two Bytes of equal length
   *
   * @param a first Bytes object
   * @param b second Bytes object (must be same length as a)
   * @return Bytes object containing the XOR result
   * @throws IllegalArgumentException if bytes are null or different lengths
   */
  public static Bytes xor(Bytes a, Bytes b) {
    if (a == null || b == null || a.size() != b.size()) {
      throw new IllegalArgumentException("Bytes must be non-null and equal length for XOR");
    }

    byte[] result = new byte[a.size()];
    for (int i = 0; i < a.size(); i++) {
      result[i] = (byte) (a.get(i) ^ b.get(i));
    }
    return Bytes.wrap(result);
  }

  /**
   * Count leading zero bits in bytes (used for Kademlia distance calculation)
   *
   * @param bytes the Bytes object to analyze
   * @return number of leading zero bits, or Integer.MAX_VALUE if bytes is null/empty
   */
  public static int leadingZeroBits(Bytes bytes) {
    if (bytes == null || bytes.isEmpty()) {
      return Integer.MAX_VALUE; // All bits are zero
    }

    int leadingZeros = 0;
    for (int i = 0; i < bytes.size(); i++) {
      byte b = bytes.get(i);
      if (b == 0) {
        leadingZeros += 8;
      } else {
        // Count leading zeros in this byte
        for (int j = 7; j >= 0; j--) {
          if ((b & (1 << j)) == 0) {
            leadingZeros++;
          } else {
            return leadingZeros;
          }
        }
        break;
      }
    }
    return leadingZeros;
  }

  /** Efficiently extract bytes from ByteBuf using Tuweni Bytes */
  public static Bytes extractBytesFromByteBuf(ByteBuf buffer) {
    int readableBytes = buffer.readableBytes();
    if (readableBytes == 0) {
      return Bytes.EMPTY;
    }

    if (buffer.hasArray()) {
      // If ByteBuf has a backing array, wrap it efficiently without copying
      byte[] array = buffer.array();
      int offset = buffer.arrayOffset() + buffer.readerIndex();
      buffer.readerIndex(buffer.readerIndex() + readableBytes); // Update reader index
      return Bytes.wrap(array, offset, readableBytes);
    } else {
      // Fallback: copy data if no backing array
      byte[] data = new byte[readableBytes];
      buffer.readBytes(data);
      return Bytes.wrap(data);
    }
  }

  /**
   * Get bytes data from object data.
   *
   * @param obj the object to convert to bytes
   * @return byte array representation of the object
   */
  public static byte[] fromObject(Object obj) {
    byte[] bytes = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
      objectOutputStream.writeObject(obj);
      objectOutputStream.flush();
      bytes = byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      log.error("Method objectToByteArray failed.", e);
    }
    return bytes;
  }

  /**
   * Get string data from bytes data using Tuweni.
   *
   * @param b the byte array to convert
   * @return string representation or null if bytes are empty
   */
  public static String toStr(byte[] b) {
    return (b == null || b.length == 0) ? null : new String(b);
  }

  /**
   * Convert byte to unsigned integer value.
   *
   * @param b the byte to convert
   * @return unsigned integer value (0-255)
   */
  public static int byte2int(byte b) {
    return b & 0xFF;
  }

  /**
   * Convert BigInteger to hex string without leading zeros (except for zero value). This is a
   * utility method to replace ByteArray.toHexString usage.
   *
   * @param value BigInteger to convert
   * @return hex string representation
   */
  public static String toHexString(BigInteger value) {
    return value.toString(16);
  }
}
