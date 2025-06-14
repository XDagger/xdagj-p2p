package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for P2pDisconnectMessage. Tests message creation, encoding, decoding, and reason
 * handling.
 */
public class P2pDisconnectMessageTest {

  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
  }

  static java.util.stream.Stream<DisconnectReason> validDisconnectReasons() {
    return java.util.Arrays.stream(DisconnectReason.values())
        .filter(r -> r != DisconnectReason.UNRECOGNIZED);
  }

  @ParameterizedTest
  @MethodSource("validDisconnectReasons")
  void testCreateP2pDisconnectMessage(DisconnectReason reason) {
    // Create a new P2pDisconnectMessage with a specific reason
    P2pDisconnectMessage message = new P2pDisconnectMessage(p2pConfig, reason);

    // Verify basic properties
    assertEquals(MessageType.DISCONNECT, message.getType(), "Message type should be DISCONNECT");
    assertNotNull(message.getData(), "Message data should not be null");

    if (reason != DisconnectReason.PEER_QUITING) {
      assertTrue(
          message.getData().size() > 0, "Message data should not be empty for non-NORMAL reasons");
    }

    // Test that valid() always returns true
    assertTrue(message.valid(), "valid() should always return true for P2pDisconnectMessage");
  }

  @ParameterizedTest
  @MethodSource("validDisconnectReasons")
  void testEncodeAndDecode(DisconnectReason reason) throws Exception {
    // 1. Create and encode the message
    P2pDisconnectMessage originalMessage = new P2pDisconnectMessage(p2pConfig, reason);
    Bytes encodedData = originalMessage.getData();

    // 2. Decode the message
    P2pDisconnectMessage decodedMessage = new P2pDisconnectMessage(p2pConfig, encodedData);

    // 3. Verify the decoded message
    assertEquals(
        originalMessage.getType(), decodedMessage.getType(), "Decoded type should match original");
    assertEquals(
        encodedData, decodedMessage.getData(), "Decoded data should match original encoded data");

    // Use reflection to access the private 'getReason' method for verification
    java.lang.reflect.Method getReasonMethod =
        P2pDisconnectMessage.class.getDeclaredMethod("getReason");
    getReasonMethod.setAccessible(true);
    DisconnectReason decodedReason = (DisconnectReason) getReasonMethod.invoke(decodedMessage);

    assertEquals(reason, decodedReason, "Decoded reason should match original");
  }

  @Test
  void testToString() {
    DisconnectReason reason = DisconnectReason.BAD_PROTOCOL;
    P2pDisconnectMessage message = new P2pDisconnectMessage(p2pConfig, reason);
    String messageString = message.toString();

    assertNotNull(messageString, "toString() should not be null");
    assertTrue(
        messageString.contains("reason: " + reason),
        "toString() should contain the disconnect reason");
  }

  @Test
  void testDataPayload() throws Exception {
    // Manually create a P2pDisconnectMessage payload to ensure it's parsed correctly
    DisconnectReason reason = DisconnectReason.DUPLICATE_PEER;
    io.xdag.p2p.proto.Connect.P2pDisconnectMessage disconnectProto =
        io.xdag.p2p.proto.Connect.P2pDisconnectMessage.newBuilder().setReason(reason).build();

    Bytes data = Bytes.wrap(disconnectProto.toByteArray());
    P2pDisconnectMessage message = new P2pDisconnectMessage(p2pConfig, data);

    // Verify the reason using reflection
    java.lang.reflect.Method getReasonMethod =
        P2pDisconnectMessage.class.getDeclaredMethod("getReason");
    getReasonMethod.setAccessible(true);
    DisconnectReason decodedReason = (DisconnectReason) getReasonMethod.invoke(message);

    assertEquals(reason, decodedReason, "Reason from payload should be parsed correctly");
  }
}
