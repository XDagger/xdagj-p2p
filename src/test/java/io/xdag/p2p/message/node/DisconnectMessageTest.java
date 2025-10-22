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
package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.ReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for DisconnectMessage. Tests message creation, encoding, decoding, and reason
 * handling.
 */
public class DisconnectMessageTest {

  @ParameterizedTest
  @EnumSource(ReasonCode.class)
  void testCreateDisconnectMessage(ReasonCode reason) {
    // Create a new DisconnectMessage with a specific reason
    DisconnectMessage message = new DisconnectMessage(reason);

    // Verify basic properties
    assertEquals(MessageCode.DISCONNECT, message.getCode(), "Message code should be DISCONNECT");
    assertEquals(reason, message.getReason(), "Reason should match");
    assertNotNull(message.getBody(), "Message body should not be null");
  }

  @ParameterizedTest
  @EnumSource(ReasonCode.class)
  void testEncodeAndDecode(ReasonCode reason) {
    // 1. Create and encode the message
    DisconnectMessage originalMessage = new DisconnectMessage(reason);
    byte[] encodedData = originalMessage.getBody();

    // 2. Decode the message
    DisconnectMessage decodedMessage = new DisconnectMessage(encodedData);

    // 3. Verify the decoded message
    assertEquals(
        originalMessage.getCode(), decodedMessage.getCode(), "Decoded code should match original");
    assertEquals(
        originalMessage.getReason(),
        decodedMessage.getReason(),
        "Decoded reason should match original");
  }

  @Test
  void testToString() {
    ReasonCode reason = ReasonCode.BAD_PEER;
    DisconnectMessage message = new DisconnectMessage(reason);
    String messageString = message.toString();

    assertNotNull(messageString, "toString() should not be null");
    assertEquals(
        "DisconnectMessage [reason=" + reason + "]",
        messageString,
        "toString() should contain the disconnect reason");
  }

  @Test
  void testAllReasonCodes() {
    // Test that all ReasonCode values can be encoded and decoded
    for (ReasonCode reason : ReasonCode.values()) {
      DisconnectMessage message = new DisconnectMessage(reason);
      byte[] encoded = message.getBody();

      DisconnectMessage decoded = new DisconnectMessage(encoded);
      assertEquals(reason, decoded.getReason(), "Reason should round-trip correctly: " + reason);
    }
  }

  @Test
  void testReasonCodeMapping() {
    // Test specific reason codes to ensure correct byte mapping
    DisconnectMessage badNetwork = new DisconnectMessage(ReasonCode.BAD_NETWORK);
    assertEquals(ReasonCode.BAD_NETWORK, badNetwork.getReason());

    DisconnectMessage tooManyPeers = new DisconnectMessage(ReasonCode.TOO_MANY_PEERS);
    assertEquals(ReasonCode.TOO_MANY_PEERS, tooManyPeers.getReason());

    DisconnectMessage badPeer = new DisconnectMessage(ReasonCode.BAD_PEER);
    assertEquals(ReasonCode.BAD_PEER, badPeer.getReason());
  }

  @Test
  void testEncodeMethod() {
    // Given
    ReasonCode reason = ReasonCode.INVALID_HANDSHAKE;
    DisconnectMessage msg = new DisconnectMessage(reason);

    // When
    io.xdag.p2p.utils.SimpleEncoder enc = new io.xdag.p2p.utils.SimpleEncoder();
    msg.encode(enc);
    byte[] encoded = enc.toBytes();

    // Then
    assertNotNull(encoded, "Encoded bytes should not be null");
    assertTrue(encoded.length > 0, "Encoded bytes should not be empty");

    // Verify decode produces same reason
    DisconnectMessage decoded = new DisconnectMessage(encoded);
    assertEquals(reason, decoded.getReason(), "Decoded reason should match");
  }

  @Test
  void testGetSendData() {
    // Given
    DisconnectMessage msg = new DisconnectMessage(ReasonCode.BAD_NETWORK_VERSION);

    // When
    org.apache.tuweni.bytes.Bytes sendData = msg.getSendData();

    // Then
    assertNotNull(sendData, "Send data should not be null");
    assertTrue(sendData.size() > 0, "Send data should not be empty");
    assertEquals(MessageCode.DISCONNECT.toByte(), sendData.get(0), "First byte should be message code");
  }

  @Test
  void testGetBody() {
    // Given
    DisconnectMessage msg = new DisconnectMessage(ReasonCode.TOO_MANY_PEERS);

    // When
    byte[] body = msg.getBody();

    // Then
    assertNotNull(body, "Body should not be null");
    assertTrue(body.length > 0, "Body should not be empty");
  }

  @Test
  void testMessageCodeIsDisconnect() {
    // Given
    DisconnectMessage msg = new DisconnectMessage(ReasonCode.BAD_PEER);

    // When/Then
    assertEquals(MessageCode.DISCONNECT, msg.getCode(), "Message code should be DISCONNECT");
    assertEquals(MessageCode.DISCONNECT, msg.getType(), "Message type should be DISCONNECT");
  }

  @Test
  void testResponseMessageClassIsNull() {
    // Given
    DisconnectMessage msg = new DisconnectMessage(ReasonCode.BAD_NETWORK);

    // When/Then
    assertNull(msg.getResponseMessageClass(), "Disconnect message should have no response class");
  }

  @Test
  void testDifferentReasonsProduceDifferentMessages() {
    // Given
    DisconnectMessage msg1 = new DisconnectMessage(ReasonCode.BAD_NETWORK);
    DisconnectMessage msg2 = new DisconnectMessage(ReasonCode.TOO_MANY_PEERS);

    // When
    byte[] body1 = msg1.getBody();
    byte[] body2 = msg2.getBody();

    // Then - bodies should be different for different reasons
    assertFalse(java.util.Arrays.equals(body1, body2), "Different reasons should produce different encoded messages");
  }

  @Test
  void testToStringContainsReason() {
    // Test multiple reason codes to ensure toString includes them
    for (ReasonCode reason : new ReasonCode[]{
        ReasonCode.BAD_NETWORK,
        ReasonCode.INVALID_HANDSHAKE,
        ReasonCode.DUPLICATED_PEER_ID}) {
      DisconnectMessage msg = new DisconnectMessage(reason);
      String str = msg.toString();
      assertTrue(str.contains(reason.toString()),
        "toString should contain reason: " + reason);
    }
  }
}
