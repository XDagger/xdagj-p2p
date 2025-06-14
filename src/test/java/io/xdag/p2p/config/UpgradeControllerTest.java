package io.xdag.p2p.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.xdag.p2p.P2pException;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for UpgradeController class. Tests data encoding/decoding and version compatibility.
 */
public class UpgradeControllerTest {

  @Test
  public void testCodeSendDataWithoutCompression() throws IOException {
    // Test with version 0 (no compression)
    Bytes originalData = Bytes.fromHexString("0x1234567890abcdef");
    Bytes encodedData = UpgradeController.codeSendData(0, originalData);

    // Should return original data without compression
    assertEquals(originalData, encodedData);
  }

  @Test
  public void testCodeSendDataWithCompression() throws IOException {
    // Test with version 1 (with compression)
    Bytes originalData = Bytes.fromHexString("0x1234567890abcdef");
    Bytes encodedData = UpgradeController.codeSendData(1, originalData);

    // Should return compressed data (different from original)
    assertNotNull(encodedData);
    // Compressed data should be different (unless data is very small)
  }

  @Test
  public void testDecodeReceiveDataWithoutCompression() throws P2pException, IOException {
    // Test with version 0 (no compression)
    Bytes originalData = Bytes.fromHexString("0x1234567890abcdef");
    Bytes decodedData = UpgradeController.decodeReceiveData(0, originalData);

    // Should return original data without decompression
    assertEquals(originalData, decodedData);
  }

  @Test
  public void testRoundTripWithCompression() throws P2pException, IOException {
    // Test encode then decode with compression
    Bytes originalData = Bytes.fromHexString("0x1234567890abcdef1122334455667788");

    // Encode with compression
    Bytes encodedData = UpgradeController.codeSendData(1, originalData);

    // Decode the encoded data
    Bytes decodedData = UpgradeController.decodeReceiveData(1, encodedData);

    // Should get back original data
    assertEquals(originalData, decodedData);
  }

  @Test
  public void testRoundTripWithoutCompression() throws P2pException, IOException {
    // Test encode then decode without compression
    Bytes originalData = Bytes.fromHexString("0x1234567890abcdef1122334455667788");

    // Encode without compression
    Bytes encodedData = UpgradeController.codeSendData(0, originalData);

    // Decode the encoded data
    Bytes decodedData = UpgradeController.decodeReceiveData(0, encodedData);

    // Should get back original data
    assertEquals(originalData, decodedData);
  }

  @Test
  public void testDecodeInvalidCompressedData() {
    // Test decoding invalid compressed data
    Bytes invalidData = Bytes.fromHexString("0xdeadbeef");

    // Should throw P2pException when trying to parse invalid compressed data
    assertThrows(
        P2pException.class,
        () -> {
          UpgradeController.decodeReceiveData(1, invalidData);
        });
  }

  @Test
  public void testEmptyData() throws P2pException, IOException {
    // Test with empty data
    Bytes emptyData = Bytes.EMPTY;

    // Without compression
    Bytes encodedEmpty = UpgradeController.codeSendData(0, emptyData);
    assertEquals(emptyData, encodedEmpty);

    Bytes decodedEmpty = UpgradeController.decodeReceiveData(0, emptyData);
    assertEquals(emptyData, decodedEmpty);
  }

  @Test
  public void testLargeData() throws P2pException, IOException {
    // Test with larger data that benefits from compression
    byte[] largeDataArray = new byte[1000];
    for (int i = 0; i < largeDataArray.length; i++) {
      largeDataArray[i] = (byte) (i % 256);
    }
    Bytes largeData = Bytes.wrap(largeDataArray);

    // Test round trip with compression
    Bytes encodedData = UpgradeController.codeSendData(1, largeData);
    Bytes decodedData = UpgradeController.decodeReceiveData(1, encodedData);

    assertEquals(largeData, decodedData);
  }

  @Test
  public void testVersionCompatibility() throws P2pException, IOException {
    // Test different version combinations
    Bytes testData = Bytes.fromHexString("0x123456789abcdef0");

    // Version 0 should not use compression
    Bytes encoded0 = UpgradeController.codeSendData(0, testData);
    assertEquals(testData, encoded0);

    // Version 1 should use compression
    Bytes encoded1 = UpgradeController.codeSendData(1, testData);
    assertNotNull(encoded1);

    // Version 2 should also use compression
    Bytes encoded2 = UpgradeController.codeSendData(2, testData);
    assertNotNull(encoded2);
  }
}
