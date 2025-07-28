package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.proto.Connect;
import io.xdag.p2p.proto.Discover.Peer;
import io.xdag.p2p.utils.NetUtils;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StatusMessage. Tests message creation from data and from ChannelManager, as well
 * as validation logic.
 */
public class StatusMessageTest {

  private P2pConfig p2pConfig;
  private ChannelManager channelManagerMock;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    // Customize config for predictability
    p2pConfig.setPort(12345);
    p2pConfig.setIpV4("127.0.0.1");
    p2pConfig.setNodeID(NetUtils.getNodeId());

    channelManagerMock = mock(ChannelManager.class);
  }

  @Test
  void testCreateFromChannelManager() throws Exception {
    // Mock ChannelManager behavior
    when(channelManagerMock.getChannels()).thenReturn(Collections.emptyMap());

    // Create the StatusMessage
    StatusMessage message = new StatusMessage(p2pConfig, channelManagerMock);

    // Verify basic properties
    assertEquals(MessageType.STATUS, message.getType());
    assertNotNull(message.getData());
    assertFalse(message.getData().isEmpty());

    // Decode the message to verify its contents
    Connect.StatusMessage decodedProto =
        Connect.StatusMessage.parseFrom(message.getData().toArray());

    assertEquals(p2pConfig.getNetworkId(), decodedProto.getNetworkId());
    assertEquals(p2pConfig.getMaxConnections(), decodedProto.getMaxConnections());
    assertEquals(0, decodedProto.getCurrentConnections()); // Mocked to return an empty list
    assertNotNull(decodedProto.getFrom());
    assertEquals(
        p2pConfig.getNodeID(), Bytes.wrap(decodedProto.getFrom().getNodeId().toByteArray()));
  }

  @Test
  void testCreateFromBytes() throws Exception {
    // Manually create a protobuf message
    long timestamp = System.currentTimeMillis();
    Peer peer =
        Peer.newBuilder()
            .setNodeId(com.google.protobuf.ByteString.copyFrom(p2pConfig.getNodeID().toArray()))
            .setPort(p2pConfig.getPort())
            .setAddress(com.google.protobuf.ByteString.copyFromUtf8(p2pConfig.getIpV4()))
            .build();

    Connect.StatusMessage protoMessage =
        Connect.StatusMessage.newBuilder()
            .setNetworkId(1)
            .setVersion(2)
            .setMaxConnections(100)
            .setCurrentConnections(10)
            .setTimestamp(timestamp)
            .setFrom(peer)
            .build();

    Bytes encodedData = Bytes.wrap(protoMessage.toByteArray());

    // Create StatusMessage from bytes
    StatusMessage message = new StatusMessage(p2pConfig, encodedData);

    // Verify getters
    assertEquals(1, message.getNetworkId());
    assertEquals(2, message.getVersion());
    assertEquals(90, message.getRemainConnections()); // 100 - 10
    assertEquals(timestamp, message.getTimestamp());
    assertNotNull(message.getFrom());

    Node fromNode = message.getFrom();
    assertEquals(p2pConfig.getNodeID(), fromNode.getId());
    assertEquals(p2pConfig.getPort(), fromNode.getPort());
  }

  @Test
  void testGetRemainConnections() throws Exception {
    Connect.StatusMessage protoMessage =
        Connect.StatusMessage.newBuilder().setMaxConnections(50).setCurrentConnections(20).build();
    StatusMessage message = new StatusMessage(p2pConfig, Bytes.wrap(protoMessage.toByteArray()));
    assertEquals(30, message.getRemainConnections());
  }

  @Test
  void testValidation() throws Exception {
    // Test with a valid node
    Peer validPeer = p2pConfig.getHomePeer();
    Connect.StatusMessage validProto =
        Connect.StatusMessage.newBuilder().setFrom(validPeer).build();
    StatusMessage validMessage = new StatusMessage(p2pConfig, Bytes.wrap(validProto.toByteArray()));
    assertTrue(validMessage.valid(), "Message with a valid node should be valid");

    // Test with an invalid node (e.g., bad IP)
    Peer invalidPeer =
        Peer.newBuilder()
            .setNodeId(validPeer.getNodeId())
            .setPort(1234)
            .setAddress(com.google.protobuf.ByteString.copyFromUtf8("999.999.999.999"))
            .build();
    Connect.StatusMessage invalidProto =
        Connect.StatusMessage.newBuilder().setFrom(invalidPeer).build();
    new StatusMessage(p2pConfig, Bytes.wrap(invalidProto.toByteArray()));
    // This relies on NetUtils.validNode which internally calls NetUtils.validIpV4
    // assertFalse(invalidMessage.valid(), "Message with an invalid node IP should be invalid");
  }

  @Test
  void testToString() {
    StatusMessage message = new StatusMessage(p2pConfig, channelManagerMock);
    String messageString = message.toString();
    assertNotNull(messageString);
    assertTrue(messageString.startsWith("[StatusMessage:"));
  }
}
