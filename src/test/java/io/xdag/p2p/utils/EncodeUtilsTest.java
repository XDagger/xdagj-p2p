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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PublicKey;
import io.xdag.crypto.keys.Signature;
import io.xdag.crypto.keys.Signer;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class EncodeUtilsTest {

  // Test public key compression and decompression
  public static String privateKey =
      "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

  @Test
  public void testSignatureAndVerify() {
    String msg = "hello world";
    String privateKey = "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

    ECKeyPair keyPair = ECKeyPair.fromHex(privateKey);

    Bytes32 hash = HashUtils.sha256(BytesUtils.fromString(msg));
    // Sign the message
    Signature sig = Signer.sign(HashUtils.sha256(BytesUtils.fromString(msg)), keyPair.getPrivateKey());

    // Recover public key from signature
    PublicKey recoveredPubKey = Signer.recoverPublicKey(HashUtils.sha256(BytesUtils.fromString(msg)), sig);

    PublicKey pubkey = keyPair.getPublicKey();

    // Verify signature
    boolean isValid = Signer.verify(hash, sig, recoveredPubKey);
    assertTrue(isValid);
    assertEquals(pubkey, recoveredPubKey);
  }

  @Test
  public void testEncode32() {
    String content = "hello world this is a test content for base32 encoding";
    String base32 = EncodeUtils.encode32(Bytes.wrap(content.getBytes()));
    assertNotNull(base32);
    assertFalse(base32.isEmpty());
  }

  @Test
  public void testEncode32AndTruncate() {
    String content =
        "hello world this is a test content for base32 encoding and truncation to 26 characters";
    String truncated = EncodeUtils.encode32AndTruncate(content);
    assertNotNull(truncated);
    assertEquals(26, truncated.length());
    assertTrue(EncodeUtils.isValidHash(truncated));
  }

  @Test
  public void testEncode64() {
    String content = "hello world this is a test content for base64 encoding";
    Bytes contentBytes = Bytes.wrap(content.getBytes(StandardCharsets.UTF_8));
    String base64 = EncodeUtils.encode64(contentBytes);
    assertNotNull(base64);
    assertFalse(base64.isEmpty());

    // Test decode
    Bytes decoded = EncodeUtils.decode64(base64);
    assertEquals(content, new String(decoded.toArray(), StandardCharsets.UTF_8));
  }

}
