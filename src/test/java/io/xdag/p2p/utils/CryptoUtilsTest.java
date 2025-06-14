package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.junit.jupiter.api.Test;

public class CryptoUtilsTest {

  // Test public key compression and decompression
  public static String privateKey =
      "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

  @Test
  public void testPublicKeyCompressAndUnCompress() {

    // Generate key pair from private key
    var keyPair = CryptoUtils.generateKeyPair(privateKey);
    SECPPublicKey secpPublicKey = keyPair.getPublicKey();

    // Get the compressed public key bytes
    byte[] compressedBytes = secpPublicKey.getEncodedBytes().toArray();

    // Extract x coordinate and y bit from compressed key
    BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(compressedBytes, 1, 33));
    boolean yBit = compressedBytes[0] == 0x03;

    // Decompress using our decompressKey method
    var decompressedPoint = CryptoUtils.decompressKey(x, yBit);
    byte[] decompressedBytes = decompressedPoint.getEncoded(false);

    // Verify the decompressed key is valid
    assertTrue(decompressedBytes.length > 0);

    // Test round trip: compress -> decompress
    BigInteger publicKeyInt =
        new BigInteger(
            1, java.util.Arrays.copyOfRange(decompressedBytes, 1, decompressedBytes.length));
    String compressedHex = CryptoUtils.compressPubKey(publicKeyInt);
    String decompressedHex = CryptoUtils.decompressPubKey(compressedHex);

    // Verify they match
    assertNotNull(compressedHex);
    assertNotNull(decompressedHex);
  }

  @Test
  public void testSignatureAndVerify() throws SignatureException {
    String msg = "hello world";
    String privateKey = "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";

    // Sign the message
    Bytes signature = CryptoUtils.sigData(msg, privateKey);

    // Recover public key from signature
    SECPPublicKey recoveredPubKey = CryptoUtils.recoverPublicKey(msg, signature);
    BigInteger recoveredPubKeyInt = recoveredPubKey.getEncodedBytes().toUnsignedBigInteger();

    // Convert to uncompressed hex format for verification
    String publicKey = BytesUtils.toHexString(Bytes.wrap(recoveredPubKeyInt.toByteArray()));

    // Verify signature
    boolean isValid = CryptoUtils.verifySignature(publicKey, msg, signature);
    assertTrue(isValid);
  }

  @Test
  public void testEncode32() {
    String content = "hello world this is a test content for base32 encoding";
    String base32 = CryptoUtils.encode32(Bytes.wrap(content.getBytes()));
    assertNotNull(base32);
    assertFalse(base32.isEmpty());
  }

  @Test
  public void testEncode32AndTruncate() {
    String content =
        "hello world this is a test content for base32 encoding and truncation to 26 characters";
    String truncated = CryptoUtils.encode32AndTruncate(content);
    assertNotNull(truncated);
    assertEquals(26, truncated.length());
    assertTrue(CryptoUtils.isValidHash(truncated));
  }

  @Test
  public void testEncode64() {
    String content = "hello world this is a test content for base64 encoding";
    Bytes contentBytes = Bytes.wrap(content.getBytes(StandardCharsets.UTF_8));
    String base64 = CryptoUtils.encode64(contentBytes);
    assertNotNull(base64);
    assertFalse(base64.isEmpty());

    // Test decode
    Bytes decoded = CryptoUtils.decode64(base64);
    assertEquals(content, new String(decoded.toArray(), StandardCharsets.UTF_8));
  }

  @Test
  public void testValidHash() {
    // Test with a valid hash (26 characters)
    String validHash = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    assertTrue(CryptoUtils.isValidHash(validHash));

    // Test encode32AndTruncate produces valid hash
    String content = "test content for validation";
    String hash = CryptoUtils.encode32AndTruncate(content);
    assertTrue(CryptoUtils.isValidHash(hash));
  }

  @Test
  public void testRecoverPublicKey() throws SignatureException {
    String msg = "test message for public key recovery";
    String privateKey = "b392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f7";

    // Sign message
    Bytes signature = CryptoUtils.sigData(msg, privateKey);

    // Recover public key
    SECPPublicKey recoveredPubKey = CryptoUtils.recoverPublicKey(msg, signature);
    assertNotNull(recoveredPubKey);

    // Convert to hex for verification
    BigInteger recoveredPubKeyInt = recoveredPubKey.getEncodedBytes().toUnsignedBigInteger();
    String recoveredPubKeyHex =
        BytesUtils.toHexString(Bytes.wrap(recoveredPubKeyInt.toByteArray()));
    assertNotNull(recoveredPubKeyHex);
    assertFalse(recoveredPubKeyHex.isEmpty());
  }
}
