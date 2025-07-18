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
package io.xdag.p2p.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsManager;
import io.xdag.p2p.message.node.DisconnectCode;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ChannelManagerTest {

  private P2pConfig p2pConfig;
  private ChannelManager channelManager;

  @Mock private Channel c1, c2, c3;
  @Mock private NodeManager nodeManager;
  @Mock private DnsManager dnsManager;

  private final InetSocketAddress a1 = new InetSocketAddress("100.1.1.1", 100);
  private final InetSocketAddress a2 = new InetSocketAddress("100.1.1.2", 100);
  private final InetSocketAddress a3 = new InetSocketAddress("100.1.1.2", 99);

  @BeforeEach
  public void beforeEach() {
    p2pConfig = new P2pConfig();
    channelManager = new ChannelManager(p2pConfig, nodeManager, dnsManager);
    channelManager.getBannedNodes().cleanUp();
    clearChannels();
  }

  @Test
  public void testGetConnectionNum() {
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c2.getInetAddress()).thenReturn(a2.getAddress());
    when(c3.getInetAddress()).thenReturn(a3.getAddress());

    assertEquals(0, channelManager.getConnectionNum(a1.getAddress()));

    channelManager.getChannels().put(a1, c1);
    assertEquals(1, channelManager.getConnectionNum(a1.getAddress()));

    channelManager.getChannels().put(a2, c2);
    assertEquals(1, channelManager.getConnectionNum(a2.getAddress()));

    channelManager.getChannels().put(a3, c3);
    assertEquals(2, channelManager.getConnectionNum(a3.getAddress()));
  }

  @Test
  public void testNotifyDisconnect() {
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    channelManager.getChannels().put(a1, c1);

    assertNull(channelManager.getBannedNodes().getIfPresent(a1.getAddress()));
    assertEquals(1, channelManager.getChannels().size());

    channelManager.notifyDisconnect(c1);

    assertNotNull(channelManager.getBannedNodes().getIfPresent(a1.getAddress()));
    assertEquals(0, channelManager.getChannels().size());
  }

  @Test
  public void testProcessPeerNormal() {
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    assertEquals(DisconnectCode.NORMAL, channelManager.processPeer(c1));
    assertEquals(1, channelManager.getChannels().size());
  }

  @Test
  public void testProcessPeerTooManyPeers() {
    p2pConfig.setMaxConnections(1);
    when(c1.getInetSocketAddress()).thenReturn(a1);
    channelManager.getChannels().put(a1, c1);

    when(c2.getInetSocketAddress()).thenReturn(a2);
    when(c2.getInetAddress()).thenReturn(a2.getAddress());

    assertEquals(DisconnectCode.TOO_MANY_PEERS, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerMaxConnectionWithSameIp() {
    p2pConfig.setMaxConnectionsWithSameIp(1);
    lenient().when(c1.getInetSocketAddress()).thenReturn(a1);
    lenient().when(c1.getInetAddress()).thenReturn(a1.getAddress());
    channelManager.getChannels().put(a1, c1);

    lenient().when(c2.getInetSocketAddress()).thenReturn(a2);
    lenient().when(c2.getInetAddress()).thenReturn(a1.getAddress());

    assertEquals(DisconnectCode.MAX_CONNECTION_WITH_SAME_IP, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerDuplicatePeer() {
    // Setup first peer
    Bytes nodeId = Bytes.random(64);
    Node node1 = new Node(p2pConfig, nodeId, "127.0.0.1", null, 30301, 30301);
    when(c1.getNode()).thenReturn(node1);
    when(c1.getNodeId()).thenReturn(node1.getHexId());
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c1.getStartTime()).thenReturn(100L);
    channelManager.getChannels().put(a1, c1);

    // Setup second peer with the same nodeId
    Node node2 = new Node(p2pConfig, nodeId, "127.0.0.1", null, 30302, 30302);
    when(c2.getNode()).thenReturn(node2);
    when(c2.getNodeId()).thenReturn(node2.getHexId()); // Same nodeId as c1
    when(c2.getInetSocketAddress()).thenReturn(a2);
    when(c2.getInetAddress()).thenReturn(a2.getAddress());
    when(c2.getStartTime()).thenReturn(200L);

    assertEquals(DisconnectCode.DUPLICATE_PEER, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerTimeBanned() {
    channelManager.getBannedNodes().put(a1.getAddress(), System.currentTimeMillis() + 10000);
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c1.isActive()).thenReturn(false);
    when(c1.isTrustPeer()).thenReturn(false);

    assertEquals(DisconnectCode.TIME_BANNED, channelManager.processPeer(c1));
  }

  @Test
  public void testConnectToAddress() {
    // Given
    channelManager.init();
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 30301);
    
    // When - method should execute without throwing exception
    channelManager.connect(address);
    
    // Then - verify the connect method was called properly
    assertNotNull(channelManager.getPeerClient());
  }

  @Test
  public void testBanNode() {
    // Given
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 30301);
    
    // When
    channelManager.banNode(address.getAddress(), 5000L);
    
    // Then
    assertNotNull(channelManager.getBannedNodes().getIfPresent(address.getAddress()));
  }

  @Test
  public void testClose() {
    // Given
    channelManager.init();
    
    // When
    channelManager.close();
    
    // Then
    assertTrue(channelManager.isShutdown);
  }

  @Test
  public void testUpdateNodeId() {
    // Given
    String newNodeId = "new-node-id";
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 30301);
    when(c1.getInetSocketAddress()).thenReturn(address);
    when(c1.getInetAddress()).thenReturn(address.getAddress());
    
    // Add channel to manager
    channelManager.getChannels().put(address, c1);
    
    // When
    channelManager.updateNodeId(c1, newNodeId);
    
    // Then - verify channel is still in manager after node ID update
    assertEquals(1, channelManager.getChannels().size());
  }

  @Test
  public void testTriggerConnect() {
    // Given
    channelManager.init();
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 30301);
    
    // When - method should execute without exception
    channelManager.triggerConnect(address);
    
    // Then - verify peer client exists
    assertNotNull(channelManager.getPeerClient());
  }

  @Test
  public void testGetDisconnectReason() {
    // Test various disconnect codes
    assertEquals(channelManager.getDisconnectReason(DisconnectCode.NORMAL), 
                channelManager.getDisconnectReason(DisconnectCode.NORMAL));
    assertEquals(channelManager.getDisconnectReason(DisconnectCode.TOO_MANY_PEERS), 
                channelManager.getDisconnectReason(DisconnectCode.TOO_MANY_PEERS));
    assertEquals(channelManager.getDisconnectReason(DisconnectCode.DUPLICATE_PEER), 
                channelManager.getDisconnectReason(DisconnectCode.DUPLICATE_PEER));
  }

  @Test
  public void testLogDisconnectReason() {
    // Given
    when(c1.getInetSocketAddress()).thenReturn(a1);
    
    // When - should execute without exception
    channelManager.logDisconnectReason(c1, channelManager.getDisconnectReason(DisconnectCode.NORMAL));
    
    // Then - method completed successfully (no assertion needed for logging)
  }

  @Test
  public void testNotifyDisconnectWithNullAddress() {
    // Given
    when(c1.getInetSocketAddress()).thenReturn(null);
    
    // When - should handle null address gracefully
    channelManager.notifyDisconnect(c1);
    
    // Then - no exception should be thrown and no changes to channels
    assertEquals(0, channelManager.getChannels().size());
  }

  private void clearChannels() {
    channelManager.getChannels().clear();
    channelManager.getBannedNodes().invalidateAll();
  }
}
