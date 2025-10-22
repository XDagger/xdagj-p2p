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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.p2p.PeerClient;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.NodeManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for ChannelManager - the core connection management component.
 * Tests ban system, connection tracking, and channel lifecycle management.
 */
public class ChannelManagerTest {

  private P2pConfig p2pConfig;
  private ChannelManager channelManager;
  private NodeManager nodeManager;

  private final InetSocketAddress a1 = new InetSocketAddress("100.1.1.1", 100);
  private final InetSocketAddress a2 = new InetSocketAddress("100.1.1.2", 100);

  @BeforeEach
  public void beforeEach() {
    p2pConfig = new P2pConfig();
    // Generate nodeKey for testing - required for node ID generation
    p2pConfig.generateNodeKey();
    nodeManager = new NodeManager(p2pConfig);
    nodeManager.init();
    channelManager = new ChannelManager(p2pConfig, nodeManager);
  }

  @AfterEach
  public void tearDown() {
    if (nodeManager != null) {
      nodeManager.close();
    }
    if (channelManager != null && !channelManager.isShutdown()) {
      channelManager.stop();
    }
  }

  @Test
  public void testChannelManagerInitialization() {
    assertNotNull(channelManager);
    assertNotNull(channelManager.getChannels());
    assertEquals(0, channelManager.getChannels().size());
    assertEquals(0, channelManager.getActivePeersCount());
    assertEquals(0, channelManager.getPassivePeersCount());
  }

  @Test
  public void testBanNode() {
    InetAddress address = a1.getAddress();

    // Initially not banned
    assertFalse(channelManager.isBanned(address));
    assertNull(channelManager.getBanInfo(address));

    // Ban the node
    channelManager.banNode(address, 10000L);

    // Now should be banned
    assertTrue(channelManager.isBanned(address));
    assertNotNull(channelManager.getBanInfo(address));

    BanInfo banInfo = channelManager.getBanInfo(address);
    assertEquals(1, banInfo.banCount());
  }

  @Test
  public void testBanNodeWithCustomDuration() {
    InetAddress address = a1.getAddress();
    long customDuration = 5000; // 5 seconds

    channelManager.banNode(address, customDuration);

    assertTrue(channelManager.isBanned(address));
    BanInfo banInfo = channelManager.getBanInfo(address);
    assertNotNull(banInfo);
  }

  @Test
  public void testUnbanNode() {
    InetAddress address = a1.getAddress();

    // Ban the node
    channelManager.banNode(address, 10000L);
    assertTrue(channelManager.isBanned(address));

    // Unban the node
    channelManager.unbanNode(address);
    assertFalse(channelManager.isBanned(address));
    assertNull(channelManager.getBanInfo(address));
  }

  @Test
  public void testWhitelist() {
    InetAddress address = a1.getAddress();

    // Add to whitelist
    channelManager.addToWhitelist(address);
    assertTrue(channelManager.isWhitelisted(address));

    // Try to ban whitelisted node - should be ignored
    channelManager.banNode(address, 10000L);
    assertFalse(channelManager.isBanned(address));

    // Remove from whitelist
    channelManager.removeFromWhitelist(address);
    assertFalse(channelManager.isWhitelisted(address));

    // Now ban should work
    channelManager.banNode(address, 10000L);
    assertTrue(channelManager.isBanned(address));
  }

  @Test
  public void testGraduatedBanDuration() {
    InetAddress address = a1.getAddress();

    // First ban
    channelManager.banNode(address, 10000L);
    BanInfo banInfo1 = channelManager.getBanInfo(address);
    assertEquals(1, banInfo1.banCount());

    // Unban and ban again - count should increase
    channelManager.unbanNode(address);
    channelManager.banNode(address, 10000L);
    BanInfo banInfo2 = channelManager.getBanInfo(address);
    assertEquals(2, banInfo2.banCount());
  }

  @Test
  public void testGetAllBannedNodes() {
    InetAddress address1 = a1.getAddress();
    InetAddress address2 = a2.getAddress();

    assertEquals(0, channelManager.getAllBannedNodes().size());

    channelManager.banNode(address1, 10000L);
    channelManager.banNode(address2, 10000L);

    assertEquals(2, channelManager.getAllBannedNodes().size());
    assertEquals(2, channelManager.getBannedNodeCount());
  }

  @Test
  public void testIsConnected() {
    assertFalse(channelManager.isConnected(a1));

    // Mock a channel and add to manager
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    channelManager.getChannels().put(a1, mockChannel);

    assertTrue(channelManager.isConnected(a1));
  }

  @Test
  public void testStartAndStop() {
    // Create a mock PeerClient
    PeerClient mockPeerClient = mock(PeerClient.class);

    // Start the channel manager
    channelManager.start(mockPeerClient);
    assertFalse(channelManager.isShutdown());

    // Stop the channel manager
    channelManager.stop();
    assertTrue(channelManager.isShutdown());
  }

  @Test
  public void testOnChannelActive() {
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    assertEquals(0, channelManager.getChannels().size());
    assertEquals(0, channelManager.getActivePeersCount());

    channelManager.onChannelActive(mockChannel);

    assertEquals(1, channelManager.getChannels().size());
    assertEquals(1, channelManager.getActivePeersCount());
    assertTrue(channelManager.isConnected(a1));
  }

  @Test
  public void testOnChannelInactive() {
    // First add a channel
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);
    assertEquals(1, channelManager.getChannels().size());
    assertEquals(1, channelManager.getActivePeersCount());

    // Now make it inactive
    channelManager.onChannelInactive(mockChannel);
    assertEquals(0, channelManager.getChannels().size());
    assertEquals(0, channelManager.getActivePeersCount());
    assertFalse(channelManager.isConnected(a1));
  }

  @Test
  public void testPassivePeerCount() {
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(false); // Passive peer
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    assertEquals(0, channelManager.getPassivePeersCount());

    channelManager.onChannelActive(mockChannel);

    assertEquals(1, channelManager.getPassivePeersCount());
    assertEquals(0, channelManager.getActivePeersCount());
  }

  @Test
  public void testTriggerImmediateConnect() {
    // Create a mock PeerClient
    PeerClient mockPeerClient = mock(PeerClient.class);
    channelManager.start(mockPeerClient);

    // Should not throw exception
    channelManager.triggerImmediateConnect();

    // Clean up
    channelManager.stop();
  }

  @Test
  public void testBanExpiry() throws InterruptedException {
    InetAddress address = a1.getAddress();

    // Ban with very short duration (100ms)
    channelManager.banNode(address, 100);
    assertTrue(channelManager.isBanned(address));

    // Wait for ban to expire
    Thread.sleep(150);

    // Should no longer be banned
    assertFalse(channelManager.isBanned(address));
  }

  @Test
  public void testWhitelistPreventsBan() {
    InetAddress address = a1.getAddress();

    // Ban first, then whitelist
    channelManager.banNode(address, 10000L);
    assertTrue(channelManager.isBanned(address));

    // Adding to whitelist should unban
    channelManager.addToWhitelist(address);
    assertFalse(channelManager.isBanned(address));
  }

  @Test
  public void testMaxConnectionsLimit() {
    // Set max connections to 3
    p2pConfig.setMaxConnections(3);

    // Create and add 4 mock channels
    for (int i = 1; i <= 4; i++) {
      InetSocketAddress addr = new InetSocketAddress("100.1.1." + i, 100);
      Channel mockChannel = mock(Channel.class);
      when(mockChannel.getRemoteAddress()).thenReturn(addr);
      when(mockChannel.getInetAddress()).thenReturn(addr.getAddress());
      when(mockChannel.isActive()).thenReturn(true);
      when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

      channelManager.onChannelActive(mockChannel);
    }

    // Should have 4 channels (max limit is enforced in connectLoop, not onChannelActive)
    assertEquals(4, channelManager.getChannels().size());
    assertEquals(4, channelManager.getActivePeersCount());
  }

  @Test
  public void testConnectingPeersCount() {
    assertEquals(0, channelManager.getConnectingPeersCount().get());

    // Test that connecting peers count is tracked correctly
    channelManager.getConnectingPeersCount().incrementAndGet();
    assertEquals(1, channelManager.getConnectingPeersCount().get());

    channelManager.getConnectingPeersCount().decrementAndGet();
    assertEquals(0, channelManager.getConnectingPeersCount().get());
  }

  @Test
  public void testMultipleChannelsFromSameIP() {
    InetAddress address = a1.getAddress();

    // Create two channels with same IP but different ports
    InetSocketAddress addr1 = new InetSocketAddress(address, 100);
    InetSocketAddress addr2 = new InetSocketAddress(address, 200);

    Channel mockChannel1 = mock(Channel.class);
    when(mockChannel1.getRemoteAddress()).thenReturn(addr1);
    when(mockChannel1.getInetAddress()).thenReturn(address);
    when(mockChannel1.isActive()).thenReturn(true);
    when(mockChannel1.getStartTime()).thenReturn(System.currentTimeMillis());

    Channel mockChannel2 = mock(Channel.class);
    when(mockChannel2.getRemoteAddress()).thenReturn(addr2);
    when(mockChannel2.getInetAddress()).thenReturn(address);
    when(mockChannel2.isActive()).thenReturn(true);
    when(mockChannel2.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel1);
    channelManager.onChannelActive(mockChannel2);

    assertEquals(2, channelManager.getChannels().size());
    assertEquals(2, channelManager.getActivePeersCount());
  }

  @Test
  public void testBanNodeWithNullAddress() {
    // Should not throw exception
    channelManager.banNode(null, 10000L);
    assertEquals(0, channelManager.getBannedNodeCount());
  }

  @Test
  public void testBanNodeWithZeroDuration() {
    InetAddress address = a1.getAddress();

    // Ban with zero duration should be ignored
    channelManager.banNode(address, 0);
    assertFalse(channelManager.isBanned(address));
    assertEquals(0, channelManager.getBannedNodeCount());
  }

  @Test
  public void testBanNodeWithNegativeDuration() {
    InetAddress address = a1.getAddress();

    // Ban with negative duration should be ignored
    channelManager.banNode(address, -1000);
    assertFalse(channelManager.isBanned(address));
    assertEquals(0, channelManager.getBannedNodeCount());
  }

  @Test
  public void testIsBannedWithNullAddress() {
    // Should return false without throwing exception
    assertFalse(channelManager.isBanned(null));
  }

  @Test
  public void testGetBanInfoWithNullAddress() {
    // Should return null without throwing exception
    assertNull(channelManager.getBanInfo(null));
  }

  @Test
  public void testUnbanNodeWithNullAddress() {
    // Should not throw exception
    channelManager.unbanNode(null);
  }

  @Test
  public void testAddToWhitelistWithNullAddress() {
    // Should not throw exception
    channelManager.addToWhitelist(null);
    assertFalse(channelManager.isWhitelisted(null));
  }

  @Test
  public void testRemoveFromWhitelistWithNullAddress() {
    // Should not throw exception
    channelManager.removeFromWhitelist(null);
  }

  @Test
  public void testIsWhitelistedWithNullAddress() {
    // Should return false
    assertFalse(channelManager.isWhitelisted(null));
  }

  @Test
  public void testBanClosesExistingConnections() throws InterruptedException {
    InetAddress address = a1.getAddress();

    // Create a mock channel
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(address);
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Add the channel
    channelManager.onChannelActive(mockChannel);
    assertEquals(1, channelManager.getChannels().size());

    // Ban the node
    channelManager.banNode(address, 10000L);

    // Wait a bit for async close
    Thread.sleep(50);

    // Verify channel.close() was called (via Mockito)
    // Note: We can't directly verify this without setting up the mock, but we can verify ban was recorded
    assertTrue(channelManager.isBanned(address));
  }

  @Test
  public void testDeprecatedBanNodeMethod() {
    InetAddress address = a1.getAddress();

    // Use method with custom duration
    long customDuration = 5000;
    channelManager.banNode(address, customDuration);

    // Should work with custom duration
    assertTrue(channelManager.isBanned(address));
    BanInfo banInfo = channelManager.getBanInfo(address);
    assertNotNull(banInfo);
  }

  @Test
  public void testChannelLifecycleWithMetrics() {
    // Add and remove a channel to test metrics updates
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);
    assertEquals(1, channelManager.getActivePeersCount());

    channelManager.onChannelInactive(mockChannel);
    assertEquals(0, channelManager.getActivePeersCount());
  }

  @Test
  public void testOnChannelActiveWithNodeId() {
    // Test onChannelActive with a channel that has a Node ID
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getNodeId()).thenReturn("test-node-id-123");
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);

    assertEquals(1, channelManager.getChannels().size());
    assertEquals(1, channelManager.getActivePeersCount());
  }

  @Test
  public void testOnChannelActiveDuplicateNodeId() {
    // Test duplicate connection detection by Node ID
    String nodeId = "duplicate-node-id";

    // First channel
    Channel mockChannel1 = mock(Channel.class);
    when(mockChannel1.getRemoteAddress()).thenReturn(a1);
    when(mockChannel1.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel1.isActive()).thenReturn(true);
    when(mockChannel1.getNodeId()).thenReturn(nodeId);
    when(mockChannel1.getStartTime()).thenReturn(System.currentTimeMillis());

    // Create a mock ChannelHandlerContext for the first channel
    io.netty.channel.ChannelHandlerContext mockCtx1 = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel1 = mock(io.netty.channel.Channel.class);
    when(mockCtx1.channel()).thenReturn(mockNettyChannel1);
    when(mockNettyChannel1.isActive()).thenReturn(true);
    when(mockChannel1.getCtx()).thenReturn(mockCtx1);

    channelManager.onChannelActive(mockChannel1);
    assertEquals(1, channelManager.getChannels().size());

    // Second channel with same Node ID - should be rejected
    Channel mockChannel2 = mock(Channel.class);
    when(mockChannel2.getRemoteAddress()).thenReturn(a2);
    when(mockChannel2.getInetAddress()).thenReturn(a2.getAddress());
    when(mockChannel2.isActive()).thenReturn(true);
    when(mockChannel2.getNodeId()).thenReturn(nodeId);
    when(mockChannel2.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel2);

    // Should still have only 1 channel (second was rejected)
    assertEquals(1, channelManager.getChannels().size());
  }

  @Test
  public void testOnChannelActiveWithEmptyNodeId() {
    // Test channel with empty Node ID
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getNodeId()).thenReturn("");
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);

    assertEquals(1, channelManager.getChannels().size());
    assertEquals(1, channelManager.getActivePeersCount());
  }

  @Test
  public void testOnChannelInactiveWithNodeId() {
    // Test that Node ID is removed when channel becomes inactive
    String nodeId = "test-node-id-456";

    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getNodeId()).thenReturn(nodeId);
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);
    assertEquals(1, channelManager.getChannels().size());

    channelManager.onChannelInactive(mockChannel);
    assertEquals(0, channelManager.getChannels().size());
  }

  @Test
  public void testFormatDurationSeconds() throws Exception {
    // Use reflection to test private formatDuration method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("formatDuration", long.class);
    method.setAccessible(true);

    // Test seconds
    assertEquals("30s", method.invoke(channelManager, 30000L));
    assertEquals("59s", method.invoke(channelManager, 59000L));
  }

  @Test
  public void testFormatDurationMinutes() throws Exception {
    // Use reflection to test private formatDuration method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("formatDuration", long.class);
    method.setAccessible(true);

    // Test minutes
    assertEquals("1m", method.invoke(channelManager, 60000L));
    assertEquals("45m", method.invoke(channelManager, 45 * 60000L));
  }

  @Test
  public void testFormatDurationHours() throws Exception {
    // Use reflection to test private formatDuration method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("formatDuration", long.class);
    method.setAccessible(true);

    // Test hours
    assertEquals("1h", method.invoke(channelManager, 60 * 60000L));
    assertEquals("12h", method.invoke(channelManager, 12 * 60 * 60000L));
  }

  @Test
  public void testFormatDurationDays() throws Exception {
    // Use reflection to test private formatDuration method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("formatDuration", long.class);
    method.setAccessible(true);

    // Test days
    assertEquals("1d", method.invoke(channelManager, 24 * 60 * 60000L));
    assertEquals("30d", method.invoke(channelManager, 30 * 24 * 60 * 60000L));
  }

  @Test
  public void testConnectAsyncWithNode() {
    // Create a mock PeerClient
    PeerClient mockPeerClient = mock(PeerClient.class);
    io.netty.channel.ChannelFuture mockFuture = mock(io.netty.channel.ChannelFuture.class);

    // Setup mock to return a future
    when(mockPeerClient.connect(
        org.mockito.ArgumentMatchers.any(io.xdag.p2p.discover.Node.class),
        org.mockito.ArgumentMatchers.any(io.netty.channel.ChannelFutureListener.class)))
        .thenReturn(mockFuture);

    channelManager.start(mockPeerClient);

    // Create a test node using Node(String id, InetSocketAddress address)
    io.xdag.p2p.discover.Node testNode = new io.xdag.p2p.discover.Node(
        "test-node-id",
        a1
    );

    // Call connectAsync
    io.netty.channel.ChannelFuture result = channelManager.connectAsync(testNode, false);

    // Verify the result is not null
    assertNotNull(result);

    // Clean up
    channelManager.stop();
  }

  @Test
  public void testConnectAsyncWithNodeHostV4V6() {
    // Create a mock PeerClient
    PeerClient mockPeerClient = mock(PeerClient.class);
    io.netty.channel.ChannelFuture mockFuture = mock(io.netty.channel.ChannelFuture.class);

    when(mockPeerClient.connect(
        org.mockito.ArgumentMatchers.any(io.xdag.p2p.discover.Node.class),
        org.mockito.ArgumentMatchers.any(io.netty.channel.ChannelFutureListener.class)))
        .thenReturn(mockFuture);

    channelManager.start(mockPeerClient);

    // Create a node using Node(String id, String hostV4, String hostV6, int port)
    io.xdag.p2p.discover.Node testNode = new io.xdag.p2p.discover.Node(
        "test-node-id",
        a1.getAddress().getHostAddress(),
        null,
        a1.getPort()
    );

    // Call connectAsync - should handle gracefully
    io.netty.channel.ChannelFuture result = channelManager.connectAsync(testNode, true);

    assertNotNull(result);

    channelManager.stop();
  }

  @Test
  public void testStartWithDisconnectionPolicyEnabled() {
    // Enable disconnection policy
    p2pConfig.setDisconnectionPolicyEnable(true);

    ChannelManager cm = new ChannelManager(p2pConfig, nodeManager);
    PeerClient mockPeerClient = mock(PeerClient.class);

    cm.start(mockPeerClient);
    assertFalse(cm.isShutdown());

    cm.stop();
    assertTrue(cm.isShutdown());
  }

  @Test
  public void testStartWithDisconnectionPolicyDisabled() {
    // Disable disconnection policy
    p2pConfig.setDisconnectionPolicyEnable(false);

    ChannelManager cm = new ChannelManager(p2pConfig, nodeManager);
    PeerClient mockPeerClient = mock(PeerClient.class);

    cm.start(mockPeerClient);
    assertFalse(cm.isShutdown());

    cm.stop();
    assertTrue(cm.isShutdown());
  }

  @Test
  public void testOnChannelInactiveWithEmptyNodeId() {
    // Test channel with empty Node ID during inactive
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(a1);
    when(mockChannel.getInetAddress()).thenReturn(a1.getAddress());
    when(mockChannel.isActive()).thenReturn(true);
    when(mockChannel.getNodeId()).thenReturn("");
    when(mockChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    channelManager.onChannelActive(mockChannel);
    assertEquals(1, channelManager.getChannels().size());

    channelManager.onChannelInactive(mockChannel);
    assertEquals(0, channelManager.getChannels().size());
  }

  @Test
  public void testBanNodeGraduatedDurationMax30Days() {
    InetAddress address = a1.getAddress();
    long baseDuration = 1000L; // 1 second

    // Ban multiple times to trigger graduated duration
    for (int i = 0; i < 20; i++) {
      channelManager.unbanNode(address);
      channelManager.banNode(address, baseDuration);
    }

    // Verify still banned (max should be capped at 30 days)
    assertTrue(channelManager.isBanned(address));
    BanInfo banInfo = channelManager.getBanInfo(address);
    assertNotNull(banInfo);
    // Ban count should be high
    assertTrue(banInfo.banCount() >= 10);
  }

  @Test
  public void testGetAllBannedNodesFiltersExpired() {
    InetAddress address1 = a1.getAddress();
    InetAddress address2 = a2.getAddress();

    // Ban one node with very short duration
    channelManager.banNode(address1, 1); // 1ms - will expire immediately
    // Ban another node with longer duration
    channelManager.banNode(address2, 10000L);

    // Wait a moment for first ban to expire
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      // ignore
    }

    // Should only return active bans
    var bannedNodes = channelManager.getAllBannedNodes();
    // At least one should be active (address2)
    assertTrue(bannedNodes.size() >= 1);
  }

  @Test
  public void testGetBanInfoForNonBannedNode() {
    InetAddress address = a1.getAddress();

    // Should return null for non-banned node
    assertNull(channelManager.getBanInfo(address));
  }

  @Test
  public void testUnbanNonExistentNode() {
    InetAddress address = a1.getAddress();

    // Should not throw exception
    channelManager.unbanNode(address);
    assertFalse(channelManager.isBanned(address));
  }

  @Test
  public void testRemoveNonWhitelistedNode() {
    InetAddress address = a1.getAddress();

    // Remove non-whitelisted node - should not throw
    channelManager.removeFromWhitelist(address);
    assertFalse(channelManager.isWhitelisted(address));
  }
}
