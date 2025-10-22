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

import io.xdag.crypto.hash.HashUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Base32;

@Slf4j(topic = "net")
public class EncodeUtils {

  /** Length for truncated base32 encoded hashes */
  private static final int truncateLength = 26;

  /** Padding character for base32/base64 encoding */
  public static final String padding = "=";

  /** Private constructor to prevent instantiation of utility class. */
  private EncodeUtils() {
    // Utility class
  }

  /**
   * Validate if a string is a valid base32 hash with fixed width.
   *
   * @param base32Hash base32 encoded hash string
   * @return true if valid
   */
  public static boolean isValidHash(String base32Hash) {
    if (base32Hash == null
        || base32Hash.length() != truncateLength
        || base32Hash.contains("\r")
        || base32Hash.contains("\n")) {
      return false;
    }
    StringBuilder sb = new StringBuilder(base32Hash);
    sb.append(padding.repeat(32 - truncateLength));
    try {
      Base32.decode(sb.toString());
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  /**
   * Encode bytes to base64 URL-safe format.
   *
   * @param content bytes to encode
   * @return base64 encoded string without padding
   */
  public static String encode64(Bytes content) {
    String base64Content =
        new String(Base64.getUrlEncoder().encode(content.toArray()), StandardCharsets.UTF_8);
    return StringUtils.stripEnd(base64Content, padding);
  }

  /**
   * Decode base64 URL-safe string to bytes.
   *
   * @param base64Content base64 string to decode
   * @return decoded bytes
   */
  public static Bytes decode64(String base64Content) {
    return Bytes.wrap(Base64.getUrlDecoder().decode(base64Content));
  }

  /**
   * Encode Tuweni Bytes to base32 format.
   *
   * @param content Tuweni Bytes to encode
   * @return base32 encoded string without padding
   */
  public static String encode32(Bytes content) {
    String base32Content = new String(Base32.encode(content.toArray()), StandardCharsets.UTF_8);
    return StringUtils.stripEnd(base32Content, padding);
  }

  /**
   * Hash content and encode with base32, then truncate to fixed length. Original logic: first get
   * the hash of string, then get first 16 bytes, last encode it with base32
   *
   * @param content string content to process
   * @return truncated base32 encoded hash
   */
  public static String encode32AndTruncate(String content) {
    Bytes contentBytes = BytesUtils.fromString(content);
    // Use SHA3 hash as in original implementation
    Bytes32 hash = HashUtils.keccak256(contentBytes);
    // Take the first 16 bytes of the hash, then encode with base32 and truncate
    return encode32(BytesUtils.take(hash, 16)).substring(0, truncateLength);
  }

  /**
   * Decode base32 string to bytes with padding if needed.
   *
   * @param content base32 string to decode
   * @return decoded bytes
   */
  public static Bytes decode32(String content) {
    int left = content.length() % 8;
    StringBuilder sb = new StringBuilder(content);
    if (left > 0) {
      sb.append(padding.repeat(8 - left));
    }
    return Bytes.wrap(Base32.decode(sb.toString()));
  }

}
