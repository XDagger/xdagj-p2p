package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.proto.Connect;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for PongMessage. Tests message creation, encoding, decoding, and validation. */
public class PongMessageTest {

  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
  }

  @Test
  void testCreatePongMessage() {
    // Create a new PongMessage
    PongMessage pongMessage = new PongMessage(p2pConfig);

    // Verify basic properties
    assertEquals(MessageType.PONG, pongMessage.getType(), "Message type should be PONG");
    assertNotNull(pongMessage.getData(), "Message data should not be null");
    assertFalse(pongMessage.getData().isEmpty(), "Message data should not be empty");

    // Verify timestamp is recent
    long now = System.currentTimeMillis();
    long messageTime = pongMessage.getTimeStamp();
    assertTrue(messageTime <= now && messageTime > now - 1000, "Timestamp should be very recent");
  }

  @Test
  void testEncodeAndDecode() throws Exception {
    // 1. Create and encode the message
    PongMessage originalMessage = new PongMessage(p2pConfig);
    Bytes encodedData = originalMessage.getData();
    long originalTimestamp = originalMessage.getTimeStamp();

    // 2. Decode the message
    PongMessage decodedMessage = new PongMessage(p2pConfig, encodedData);

    // 3. Verify the decoded message
    assertEquals(
        originalMessage.getType(), decodedMessage.getType(), "Decoded type should match original");
    assertEquals(
        originalTimestamp,
        decodedMessage.getTimeStamp(),
        "Decoded timestamp should match original");
    assertEquals(
        encodedData, decodedMessage.getData(), "Decoded data should match original encoded data");
  }

  @Test
  void testValidation() {
    // A newly created message should be valid
    PongMessage validMessage = new PongMessage(p2pConfig);
    assertTrue(validMessage.valid(), "A new PongMessage should be valid");
  }

  @Test
  void testDataPayload() throws Exception {
    // Manually create a KeepAliveMessage to ensure the payload is parsed correctly
    long specificTimestamp = 9876543210L;
    Connect.KeepAliveMessage keepAlive =
        Connect.KeepAliveMessage.newBuilder().setTimestamp(specificTimestamp).build();

    Bytes data = Bytes.wrap(keepAlive.toByteArray());
    PongMessage message = new PongMessage(p2pConfig, data);

    assertEquals(
        specificTimestamp,
        message.getTimeStamp(),
        "Timestamp from payload should be parsed correctly");
  }
}
