package io.xdag.p2p.utils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Base32;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

@Slf4j(topic = "net")
public class CryptoUtils {

  /** The name of the elliptic curve used for cryptographic operations */
  public static final String CURVE_NAME = "secp256k1";

  /** Parameters for the secp256k1 elliptic curve */
  public static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName(CURVE_NAME);

  /** Elliptic curve domain parameters for cryptographic operations */
  public static final ECDomainParameters CURVE =
      new ECDomainParameters(
          CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());

  /** SECP256K1 instance for signing and verification operations */
  public static final SECP256K1 SECP256K1 = new SECP256K1();

  /** Half of the curve order used for signature canonicalization */
  static final BigInteger HALF_CURVE_ORDER = CURVE_PARAMS.getN().shiftRight(1);

  /** Length for truncated base32 encoded hashes */
  private static final int truncateLength = 26;

  /** Padding character for base32/base64 encoding */
  public static final String padding = "=";

  /** Private constructor to prevent instantiation of utility class. */
  private CryptoUtils() {
    // Utility class
  }

  /**
   * Compress a public key to its x coordinate and y bit format.
   *
   * @param pubKey the public key as BigInteger
   * @return hex string of compressed public key (33 bytes: 02/03 + 32 bytes x coordinate)
   */
  public static String compressPubKey(BigInteger pubKey) {
    String pubKeyYPrefix = pubKey.testBit(0) ? "03" : "02";
    String pubKeyHex = pubKey.toString(16);
    String pubKeyX = pubKeyHex.substring(0, 64);
    return pubKeyYPrefix + pubKeyX;
  }

  /**
   * Decompress a compressed public key to its full uncompressed format.
   *
   * @param hexPubKey compressed public key in hex format
   * @return uncompressed public key in hex format
   */
  public static String decompressPubKey(String hexPubKey) {
    // Use the already defined CURVE parameters instead of redefining them
    Bytes pubKeyBytes = BytesUtils.fromHexString(hexPubKey);
    ECPoint ecPoint = CURVE.getCurve().decodePoint(pubKeyBytes.toArray());
    byte[] encoded = ecPoint.getEncoded(false);
    BigInteger n = new BigInteger(1, Arrays.copyOfRange(encoded, 1, encoded.length));
    return n.toString(16);
  }

  /**
   * Generate a KeyPair from a private key hex string.
   *
   * @param privateKey private key in hex format
   * @return KeyPair object
   */
  public static KeyPair generateKeyPair(String privateKey) {
    return KeyPair.create(
        SECPPrivateKey.create(Bytes32.fromHexString(privateKey), CURVE_NAME), CURVE, CURVE_NAME);
  }

  /**
   * Sign a message with a private key. The produced signature is in the 65-byte [R || S || V]
   * format where V is 0 or 1.
   *
   * @param msg message to sign
   * @param privateKey private key in hex format
   * @return signature bytes
   */
  public static Bytes sigData(String msg, String privateKey) {
    KeyPair keyPair = generateKeyPair(privateKey);
    Bytes32 hash = Hash.sha256(BytesUtils.fromString(msg));
    SECPSignature signature = SECP256K1.sign(hash, keyPair);
    return signature.encodedBytes();
  }

  /**
   * Recover public key from message signature.
   *
   * @param msg original message
   * @param sig signature bytes
   * @return recovered public key
   */
  public static SECPPublicKey recoverPublicKey(String msg, Bytes sig) {
    int recId = sig.get(64);
    if (recId < 27) {
      recId += 27;
    }

    Bytes32 hash = Hash.sha256(BytesUtils.fromString(msg));
    SECPSignature secpSignature = SECP256K1.decodeSignature(sig);
    return SECP256K1.recoverPublicKeyFromSignature(hash, secpSignature).get();
  }

  /**
   * Verify signature using provided public key.
   *
   * @param publicKey uncompressed hex public key
   * @param msg message that was signed
   * @param sig signature bytes
   * @return true if signature is valid
   * @throws SignatureException if signature verification fails
   */
  public static boolean verifySignature(String publicKey, String msg, Bytes sig)
      throws SignatureException {
    SECPSignature secpSignature = SECP256K1.decodeSignature(sig);

    // Convert hex public key to SECPPublicKey
    BigInteger pubKeyInt = new BigInteger(publicKey, 16);
    SECPPublicKey secpPublicKey = SECPPublicKey.create(pubKeyInt, CURVE_NAME);

    // Hash the message
    Bytes32 hash = Hash.sha256(BytesUtils.fromString(msg));

    // Verify signature using the provided public key
    return SECP256K1.verify(hash, secpSignature, secpPublicKey);
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
    Bytes32 hash = Hash.keccak256(contentBytes);
    // Take first 16 bytes of the hash, then encode with base32 and truncate
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

  /**
   * Decompress a compressed public key (x coordinate and low-bit of y-coordinate).
   *
   * @param xBN x coordinate as BigInteger
   * @param yBit y coordinate low bit
   * @return decompressed ECPoint
   */
  public static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
    X9IntegerConverter x9 = new X9IntegerConverter();
    byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
    compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
    return CURVE.getCurve().decodePoint(compEnc);
  }

  /**
   * Converts a signature to its canonical form.
   *
   * @param signature the signature to convert
   * @return the canonical SECPSignature
   */
  public static SECPSignature toCanonical(SECPSignature signature) {
    if (signature.getS().compareTo(HALF_CURVE_ORDER) > 0) {
      return SECPSignature.create(
          signature.getR(), CURVE.getN().subtract(signature.getS()), (byte) 0, CURVE.getN());
    }
    return signature;
  }
}
