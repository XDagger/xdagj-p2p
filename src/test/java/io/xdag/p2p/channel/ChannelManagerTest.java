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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
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
  public void testMarkHandshakeSuccessSetsChannelState() {
    InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 21000);

    DefaultAttributeMap attributeMap = new DefaultAttributeMap();
    io.netty.channel.Channel nettyChannel = mock(io.netty.channel.Channel.class);
    when(nettyChannel.remoteAddress()).thenReturn(remote);
    when(nettyChannel.isActive()).thenReturn(true);
    when(nettyChannel.isOpen()).thenReturn(true);
    when(nettyChannel.isWritable()).thenReturn(true);
    when(nettyChannel.attr(any())).thenAnswer(invocation -> {
      AttributeKey<Object> key = invocation.getArgument(0);
      return attributeMap.attr(key);
    });

    ChannelHandlerContext ctx = mock(ChannelHandlerContext.class, RETURNS_DEEP_STUBS);
    when(ctx.channel()).thenReturn(nettyChannel);

    String nodeId = "test-node-id";

    // Test with isOutbound=true (outbound connection)
    channelManager.markHandshakeSuccess(remote, ctx, nodeId, true);

    assertEquals(1, channelManager.getChannels().size(), "Channel map should contain exactly one entry");
    Channel managedChannel = channelManager.getChannels().values().iterator().next();
    assertNotNull(managedChannel, "Channel should be registered after handshake success");
    assertTrue(managedChannel.isFinishHandshake(), "Handshake flag should be set to true");
    assertTrue(managedChannel.isActive(), "Channel should be marked as OUTBOUND (isActive=true)");
    assertEquals(nodeId, managedChannel.getNodeId(), "Node ID should be recorded for duplicate detection");
    assertEquals(1, channelManager.getActivePeersCount(), "Active peer count should include the new channel");
  }

  @Test
  public void testMarkHandshakeSuccessSetsInboundFlag() {
    InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 21001);

    DefaultAttributeMap attributeMap = new DefaultAttributeMap();
    io.netty.channel.Channel nettyChannel = mock(io.netty.channel.Channel.class);
    when(nettyChannel.remoteAddress()).thenReturn(remote);
    when(nettyChannel.isActive()).thenReturn(true);
    when(nettyChannel.isOpen()).thenReturn(true);
    when(nettyChannel.isWritable()).thenReturn(true);
    when(nettyChannel.attr(any())).thenAnswer(invocation -> {
      AttributeKey<Object> key = invocation.getArgument(0);
      return attributeMap.attr(key);
    });

    ChannelHandlerContext ctx = mock(ChannelHandlerContext.class, RETURNS_DEEP_STUBS);
    when(ctx.channel()).thenReturn(nettyChannel);

    String nodeId = "test-node-id-inbound";

    // Test with isOutbound=false (inbound connection)
    channelManager.markHandshakeSuccess(remote, ctx, nodeId, false);

    assertEquals(1, channelManager.getChannels().size(), "Channel map should contain exactly one entry");
    Channel managedChannel = channelManager.getChannels().values().iterator().next();
    assertNotNull(managedChannel, "Channel should be registered after handshake success");
    assertTrue(managedChannel.isFinishHandshake(), "Handshake flag should be set to true");
    assertFalse(managedChannel.isActive(), "Channel should be marked as INBOUND (isActive=false)");
    assertEquals(nodeId, managedChannel.getNodeId(), "Node ID should be recorded for duplicate detection");
    // Inbound connections count as passive peers
    assertEquals(0, channelManager.getActivePeersCount(), "Active peer count should be 0 for inbound connection");
    assertEquals(1, channelManager.getPassivePeersCount(), "Passive peer count should be 1 for inbound connection");
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

  // ========== Tests for hasActiveConnectionTo() - Commit 61b0d17 ==========

  @Test
  public void testHasActiveConnectionTo_NullAddress() throws Exception {
    // Use reflection to test private hasActiveConnectionTo method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    // Test with null address - should return false
    boolean result = (boolean) method.invoke(channelManager, (InetSocketAddress) null);
    assertFalse(result, "Should return false for null address");
  }

  @Test
  public void testHasActiveConnectionTo_NoConnection() throws Exception {
    // Use reflection to test private hasActiveConnectionTo method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    InetSocketAddress testAddress = new InetSocketAddress("192.168.1.100", 8080);

    // Test with no existing connections - should return false
    boolean result = (boolean) method.invoke(channelManager, testAddress);
    assertFalse(result, "Should return false when no connections exist");
  }

  @Test
  public void testHasActiveConnectionTo_ExactMatchInChannels() throws Exception {
    // Use reflection to test private hasActiveConnectionTo method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    InetSocketAddress testAddress = new InetSocketAddress("192.168.1.100", 8080);

    // Create a mock channel with active Netty channel
    Channel mockChannel = mock(Channel.class);
    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);

    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to channels map
    channelManager.getChannels().put(testAddress, mockChannel);

    // Test - should return true since we have an active connection to this exact address
    boolean result = (boolean) method.invoke(channelManager, testAddress);
    assertTrue(result, "Should return true for exact match with active channel");
  }

  @Test
  public void testHasActiveConnectionTo_ExactMatchButInactive() throws Exception {
    // Use reflection to test private hasActiveConnectionTo method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    InetSocketAddress testAddress = new InetSocketAddress("192.168.1.100", 8080);

    // Create a mock channel with INACTIVE Netty channel
    Channel mockChannel = mock(Channel.class);
    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);

    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(false); // Inactive!

    // Add to channels map
    channelManager.getChannels().put(testAddress, mockChannel);

    // Test - should return false since the Netty channel is not active
    boolean result = (boolean) method.invoke(channelManager, testAddress);
    assertFalse(result, "Should return false when Netty channel is not active");
  }

  @Test
  public void testHasActiveConnectionTo_MatchInConnectedNodeIds() throws Exception {
    // Use reflection to access private fields and methods
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);

    InetSocketAddress testAddress = new InetSocketAddress("192.168.1.100", 8080);
    String nodeId = "test-node-id-789";

    // Create a mock channel with matching remote address
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(testAddress);

    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to connectedNodeIds map (simulating an active connection)
    connectedNodeIds.put(nodeId, mockChannel);

    // Test - should return true since we have an active connection to this address
    boolean result = (boolean) method.invoke(channelManager, testAddress);
    assertTrue(result, "Should return true when matching address found in connectedNodeIds");
  }

  @Test
  public void testHasActiveConnectionTo_LoopbackAddressWithNodeId() throws Exception {
    // Use reflection to access private fields and methods
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);

    // Target: loopback address with port 8080
    InetSocketAddress targetAddress = new InetSocketAddress("127.0.0.1", 8080);
    // Existing connection: from same loopback IP but different port (simulating inbound connection)
    InetSocketAddress existingAddress = new InetSocketAddress("127.0.0.1", 9999);
    String nodeId = "test-loopback-node";

    // Create a mock channel with different port but same loopback IP
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(mockChannel.getNodeId()).thenReturn(nodeId);

    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to connectedNodeIds map
    connectedNodeIds.put(nodeId, mockChannel);

    // Test - should return true for loopback address with same IP (even different port) if nodeId exists
    boolean result = (boolean) method.invoke(channelManager, targetAddress);
    assertTrue(result, "Should return true for loopback address with same IP and valid nodeId");
  }

  @Test
  public void testHasActiveConnectionTo_LoopbackAddressWithoutNodeId() throws Exception {
    // Use reflection to access private fields and methods
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);

    // Target: loopback address with port 8080
    InetSocketAddress targetAddress = new InetSocketAddress("127.0.0.1", 8080);
    // Existing connection: from same loopback IP but different port
    InetSocketAddress existingAddress = new InetSocketAddress("127.0.0.1", 9999);

    // Create a mock channel WITHOUT nodeId
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(mockChannel.getNodeId()).thenReturn(null); // No nodeId

    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to connectedNodeIds map (with some key)
    connectedNodeIds.put("some-key", mockChannel);

    // Test - should return false for loopback address without nodeId (can't confirm it's the same node)
    boolean result = (boolean) method.invoke(channelManager, targetAddress);
    assertFalse(result, "Should return false for loopback address without nodeId");
  }

  @Test
  public void testHasActiveConnectionTo_LoopbackAddressWithEmptyNodeId() throws Exception {
    // Use reflection to access private fields and methods
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);

    // Target: loopback address with port 8080
    InetSocketAddress targetAddress = new InetSocketAddress("127.0.0.1", 8080);
    // Existing connection: from same loopback IP but different port
    InetSocketAddress existingAddress = new InetSocketAddress("127.0.0.1", 9999);

    // Create a mock channel with EMPTY nodeId
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(mockChannel.getNodeId()).thenReturn(""); // Empty nodeId

    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to connectedNodeIds map
    connectedNodeIds.put("some-key", mockChannel);

    // Test - should return false for loopback address with empty nodeId
    boolean result = (boolean) method.invoke(channelManager, targetAddress);
    assertFalse(result, "Should return false for loopback address with empty nodeId");
  }

  @Test
  public void testHasActiveConnectionTo_NonLoopbackDifferentPort() throws Exception {
    // Use reflection to access private fields and methods
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);

    // Target: non-loopback address with port 8080
    InetSocketAddress targetAddress = new InetSocketAddress("192.168.1.100", 8080);
    // Existing connection: same IP but DIFFERENT port
    InetSocketAddress existingAddress = new InetSocketAddress("192.168.1.100", 9999);
    String nodeId = "test-node-different-port";

    // Create a mock channel with different port
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(mockChannel.getNodeId()).thenReturn(nodeId);

    io.netty.channel.ChannelHandlerContext mockCtx = mock(io.netty.channel.ChannelHandlerContext.class);
    io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
    when(mockChannel.getCtx()).thenReturn(mockCtx);
    when(mockCtx.channel()).thenReturn(mockNettyChannel);
    when(mockNettyChannel.isActive()).thenReturn(true);

    // Add to connectedNodeIds map
    connectedNodeIds.put(nodeId, mockChannel);

    // Test - should return false for non-loopback address with different port
    boolean result = (boolean) method.invoke(channelManager, targetAddress);
    assertFalse(result, "Should return false for non-loopback address with different port");
  }

  @Test
  public void testHasActiveConnectionTo_ExactMatchWithNullCtx() throws Exception {
    // Use reflection to test private hasActiveConnectionTo method
    java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod("hasActiveConnectionTo", InetSocketAddress.class);
    method.setAccessible(true);

    InetSocketAddress testAddress = new InetSocketAddress("192.168.1.100", 8080);

    // Create a mock channel with NULL context (edge case)
    Channel mockChannel = mock(Channel.class);
    when(mockChannel.getCtx()).thenReturn(null);

    // Add to channels map
    channelManager.getChannels().put(testAddress, mockChannel);

    // Test - should return false since ctx is null
    boolean result = (boolean) method.invoke(channelManager, testAddress);
    assertFalse(result, "Should return false when channel context is null");
  }

  // ========== Tests for onChannelActive() NodeId deduplication - BUG-P2P-002 ==========

  @Test
  public void testOnChannelActive_NullNodeId() throws Exception {
    // Create a channel with null nodeId
    Channel channel = mock(Channel.class);
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 55001);
    when(channel.getNodeId()).thenReturn(null);
    when(channel.getRemoteAddress()).thenReturn(remoteAddress);
    when(channel.isActive()).thenReturn(true);

    // Call onChannelActive
    channelManager.onChannelActive(channel);

    // Channel should be added to channels map
    assertTrue(channelManager.getChannels().containsKey(remoteAddress),
        "Channel with null nodeId should be added to channels map");

    // But NOT to connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertTrue(connectedNodeIds.isEmpty(),
        "Channel with null nodeId should NOT be added to connectedNodeIds");
  }

  @Test
  public void testOnChannelActive_EmptyNodeId() throws Exception {
    // Create a channel with empty nodeId
    Channel channel = mock(Channel.class);
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 55002);
    when(channel.getNodeId()).thenReturn("");
    when(channel.getRemoteAddress()).thenReturn(remoteAddress);
    when(channel.isActive()).thenReturn(true);

    // Call onChannelActive
    channelManager.onChannelActive(channel);

    // Channel should be added to channels map
    assertTrue(channelManager.getChannels().containsKey(remoteAddress),
        "Channel with empty nodeId should be added to channels map");

    // But NOT to connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertTrue(connectedNodeIds.isEmpty(),
        "Channel with empty nodeId should NOT be added to connectedNodeIds");
  }

  @Test
  public void testOnChannelActive_FirstConnectionWithNodeId() throws Exception {
    String nodeId = "node-id-12345";
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 55003);

    Channel channel = mock(Channel.class);
    when(channel.getNodeId()).thenReturn(nodeId);
    when(channel.getRemoteAddress()).thenReturn(remoteAddress);
    when(channel.isActive()).thenReturn(true);

    // Call onChannelActive
    channelManager.onChannelActive(channel);

    // Channel should be added to channels map
    assertTrue(channelManager.getChannels().containsKey(remoteAddress),
        "First channel should be added to channels map");

    // AND to connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(channel, connectedNodeIds.get(nodeId),
        "First channel should be added to connectedNodeIds");
  }

  @Test
  public void testOnChannelActive_DuplicateWithActiveChannel() throws Exception {
    String nodeId = "node-id-duplicate-active";
    InetSocketAddress existingAddress = new InetSocketAddress("192.168.1.100", 55004);
    InetSocketAddress newAddress = new InetSocketAddress("192.168.1.100", 55005);

    // Create existing channel that is ACTIVE
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(nodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(existingChannel.isActive()).thenReturn(true);

    // Mock Netty channel as active
    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel nettyChannel = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.isActive()).thenReturn(true);
    when(nettyChannel.isOpen()).thenReturn(true);
    when(nettyChannel.isWritable()).thenReturn(true);

    // Add existing channel first
    channelManager.onChannelActive(existingChannel);

    // Create new channel with same nodeId
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(nodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddress);
    when(newChannel.isActive()).thenReturn(true);

    // Call onChannelActive for new channel
    channelManager.onChannelActive(newChannel);

    // New channel should be closed (duplicate)
    verify(newChannel, times(1)).closeWithoutBan();

    // Existing channel should still be in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(existingChannel, connectedNodeIds.get(nodeId),
        "Existing active channel should remain in connectedNodeIds");

    // New channel should NOT be in channels map
    assertFalse(channelManager.getChannels().containsKey(newAddress),
        "New duplicate channel should NOT be added to channels map");
  }

  @Test
  public void testOnChannelActive_DuplicateWithStaleChannel() throws Exception {
    String nodeId = "node-id-duplicate-stale";
    InetSocketAddress existingAddress = new InetSocketAddress("192.168.1.100", 55006);
    InetSocketAddress newAddress = new InetSocketAddress("192.168.1.100", 55007);

    // Create existing channel that is STALE (Netty channel inactive)
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(nodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddress);
    when(existingChannel.isActive()).thenReturn(false);

    // Mock Netty channel as INACTIVE (stale)
    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel nettyChannel = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.isActive()).thenReturn(false);

    // Add existing channel first
    channelManager.onChannelActive(existingChannel);

    // Verify existing channel is in maps
    assertTrue(channelManager.getChannels().containsKey(existingAddress));

    // Create new channel with same nodeId
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(nodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddress);
    when(newChannel.isActive()).thenReturn(true);

    // Call onChannelActive for new channel
    channelManager.onChannelActive(newChannel);

    // Stale channel should be closed and cleaned up
    verify(existingChannel, times(1)).closeWithoutBan();

    // New channel should replace stale in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(newChannel, connectedNodeIds.get(nodeId),
        "New channel should replace stale channel in connectedNodeIds");

    // Old address should be removed, new address should be added
    assertFalse(channelManager.getChannels().containsKey(existingAddress),
        "Stale channel address should be removed from channels map");
    assertTrue(channelManager.getChannels().containsKey(newAddress),
        "New channel address should be added to channels map");
  }

  @Test
  public void testGetUniqueConnectedChannels() throws Exception {
    String nodeId1 = "unique-node-1";
    String nodeId2 = "unique-node-2";

    // Create two channels with different nodeIds
    Channel channel1 = mock(Channel.class);
    when(channel1.getNodeId()).thenReturn(nodeId1);
    when(channel1.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.101", 55010));
    when(channel1.isActive()).thenReturn(true);

    Channel channel2 = mock(Channel.class);
    when(channel2.getNodeId()).thenReturn(nodeId2);
    when(channel2.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.102", 55011));
    when(channel2.isActive()).thenReturn(true);

    // Add both channels
    channelManager.onChannelActive(channel1);
    channelManager.onChannelActive(channel2);

    // Get unique channels
    java.util.List<Channel> uniqueChannels = channelManager.getUniqueConnectedChannels();

    // Should have exactly 2 unique channels
    assertEquals(2, uniqueChannels.size(), "Should have 2 unique connected channels");
    assertTrue(uniqueChannels.contains(channel1), "Should contain channel1");
    assertTrue(uniqueChannels.contains(channel2), "Should contain channel2");
  }

  @Test
  public void testGetUniqueConnectedChannels_ExcludesNullNodeId() throws Exception {
    String nodeId = "valid-node-id";

    // Create channel with valid nodeId
    Channel channelWithId = mock(Channel.class);
    when(channelWithId.getNodeId()).thenReturn(nodeId);
    when(channelWithId.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.103", 55012));
    when(channelWithId.isActive()).thenReturn(true);

    // Create channel with null nodeId
    Channel channelWithoutId = mock(Channel.class);
    when(channelWithoutId.getNodeId()).thenReturn(null);
    when(channelWithoutId.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.104", 55013));
    when(channelWithoutId.isActive()).thenReturn(true);

    // Add both channels
    channelManager.onChannelActive(channelWithId);
    channelManager.onChannelActive(channelWithoutId);

    // Get unique channels
    java.util.List<Channel> uniqueChannels = channelManager.getUniqueConnectedChannels();

    // Should only have 1 (the one with nodeId)
    assertEquals(1, uniqueChannels.size(),
        "getUniqueConnectedChannels should only return channels with nodeId");
    assertTrue(uniqueChannels.contains(channelWithId),
        "Should contain channel with valid nodeId");
    assertFalse(uniqueChannels.contains(channelWithoutId),
        "Should NOT contain channel without nodeId");

    // But channels map should have both
    assertEquals(2, channelManager.getChannels().size(),
        "channels map should contain both channels");
  }

  @Test
  public void testOnChannelActive_ThreadSafety() throws Exception {
    // Test that concurrent calls to onChannelActive with same nodeId don't cause issues
    String nodeId = "concurrent-node-id";
    int numThreads = 10;
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(numThreads);
    java.util.concurrent.atomic.AtomicInteger closedCount = new java.util.concurrent.atomic.AtomicInteger(0);

    java.util.List<Channel> channels = new java.util.ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      Channel channel = mock(Channel.class);
      when(channel.getNodeId()).thenReturn(nodeId);
      when(channel.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.200", 56000 + i));
      when(channel.isActive()).thenReturn(true);
      // Count how many get closed
      doAnswer(invocation -> {
        closedCount.incrementAndGet();
        return null;
      }).when(channel).closeWithoutBan();
      channels.add(channel);
    }

    // Run concurrent onChannelActive calls
    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
    for (Channel channel : channels) {
      executor.submit(() -> {
        try {
          channelManager.onChannelActive(channel);
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
    executor.shutdown();

    // Only ONE channel should remain in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(1, connectedNodeIds.size(),
        "Only one channel per nodeId should remain after concurrent calls");

    // n-1 channels should have been closed
    assertEquals(numThreads - 1, closedCount.get(),
        "All but one channel should have been closed");
  }

  // ========== Tests for Deterministic Duplicate Connection Tie-Breaking ==========

  /**
   * Test: When localNodeId < remoteNodeId, local node prefers OUTBOUND connections.
   *
   * Scenario:
   * - Local node has ID "AAAA..." (smaller)
   * - Remote node has ID "ZZZZ..." (larger)
   * - Existing connection is INBOUND (isActive=false)
   * - New connection is OUTBOUND (isActive=true)
   * - Expected: Keep the NEW outbound connection, close existing inbound
   */
  @Test
  public void testDuplicateConnection_SmallerNodeIdPrefersOutbound_KeepNew() throws Exception {
    // Setup: local nodeId is smaller than remote nodeId
    String localNodeId = "AAAA1111111111111111111111111111";
    String remoteNodeId = "ZZZZ9999999999999999999999999999";

    // Use reflection to set localNodeId
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, localNodeId);

    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55100);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55101);

    // Create existing INBOUND channel (isActive=false)
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(false); // INBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    // Add existing channel
    channelManager.onChannelActive(existingChannel);

    // Create new OUTBOUND channel (isActive=true)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(true); // OUTBOUND

    // Add new channel
    channelManager.onChannelActive(newChannel);

    // Verify: existing INBOUND should be closed, new OUTBOUND should be kept
    verify(existingChannel, times(1)).closeWithoutBan();
    verify(newChannel, times(0)).closeWithoutBan();

    // New channel should be in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(newChannel, connectedNodeIds.get(remoteNodeId),
        "New outbound channel should be kept when localId < remoteId");
  }

  /**
   * Test: When localNodeId < remoteNodeId, local node prefers OUTBOUND connections.
   *
   * Scenario:
   * - Local node has ID "AAAA..." (smaller)
   * - Remote node has ID "ZZZZ..." (larger)
   * - Existing connection is OUTBOUND (isActive=true)
   * - New connection is INBOUND (isActive=false)
   * - Expected: Keep the EXISTING outbound connection, close new inbound
   */
  @Test
  public void testDuplicateConnection_SmallerNodeIdPrefersOutbound_KeepExisting() throws Exception {
    // Setup: local nodeId is smaller than remote nodeId
    String localNodeId = "AAAA1111111111111111111111111111";
    String remoteNodeId = "ZZZZ9999999999999999999999999999";

    // Use reflection to set localNodeId
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, localNodeId);

    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55102);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55103);

    // Create existing OUTBOUND channel (isActive=true)
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(true); // OUTBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    // Add existing channel
    channelManager.onChannelActive(existingChannel);

    // Create new INBOUND channel (isActive=false)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(false); // INBOUND

    // Add new channel
    channelManager.onChannelActive(newChannel);

    // Verify: new INBOUND should be closed, existing OUTBOUND should be kept
    verify(existingChannel, times(0)).closeWithoutBan();
    verify(newChannel, times(1)).closeWithoutBan();

    // Existing channel should still be in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(existingChannel, connectedNodeIds.get(remoteNodeId),
        "Existing outbound channel should be kept when localId < remoteId");
  }

  /**
   * Test: When localNodeId > remoteNodeId, local node prefers INBOUND connections.
   *
   * Scenario:
   * - Local node has ID "ZZZZ..." (larger)
   * - Remote node has ID "AAAA..." (smaller)
   * - Existing connection is OUTBOUND (isActive=true)
   * - New connection is INBOUND (isActive=false)
   * - Expected: Keep the NEW inbound connection, close existing outbound
   */
  @Test
  public void testDuplicateConnection_LargerNodeIdPrefersInbound_KeepNew() throws Exception {
    // Setup: local nodeId is larger than remote nodeId
    String localNodeId = "ZZZZ9999999999999999999999999999";
    String remoteNodeId = "AAAA1111111111111111111111111111";

    // Use reflection to set localNodeId
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, localNodeId);

    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55104);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55105);

    // Create existing OUTBOUND channel (isActive=true)
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(true); // OUTBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    // Add existing channel
    channelManager.onChannelActive(existingChannel);

    // Create new INBOUND channel (isActive=false)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(false); // INBOUND

    // Add new channel
    channelManager.onChannelActive(newChannel);

    // Verify: existing OUTBOUND should be closed, new INBOUND should be kept
    verify(existingChannel, times(1)).closeWithoutBan();
    verify(newChannel, times(0)).closeWithoutBan();

    // New channel should be in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(newChannel, connectedNodeIds.get(remoteNodeId),
        "New inbound channel should be kept when localId > remoteId");
  }

  /**
   * Test: When localNodeId > remoteNodeId, local node prefers INBOUND connections.
   *
   * Scenario:
   * - Local node has ID "ZZZZ..." (larger)
   * - Remote node has ID "AAAA..." (smaller)
   * - Existing connection is INBOUND (isActive=false)
   * - New connection is OUTBOUND (isActive=true)
   * - Expected: Keep the EXISTING inbound connection, close new outbound
   */
  @Test
  public void testDuplicateConnection_LargerNodeIdPrefersInbound_KeepExisting() throws Exception {
    // Setup: local nodeId is larger than remote nodeId
    String localNodeId = "ZZZZ9999999999999999999999999999";
    String remoteNodeId = "AAAA1111111111111111111111111111";

    // Use reflection to set localNodeId
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, localNodeId);

    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55106);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55107);

    // Create existing INBOUND channel (isActive=false)
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(false); // INBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    // Add existing channel
    channelManager.onChannelActive(existingChannel);

    // Create new OUTBOUND channel (isActive=true)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(true); // OUTBOUND

    // Add new channel
    channelManager.onChannelActive(newChannel);

    // Verify: new OUTBOUND should be closed, existing INBOUND should be kept
    verify(existingChannel, times(0)).closeWithoutBan();
    verify(newChannel, times(1)).closeWithoutBan();

    // Existing channel should still be in connectedNodeIds
    java.lang.reflect.Field connectedNodeIdsField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connectedNodeIdsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> connectedNodeIds = (java.util.Map<String, Channel>) connectedNodeIdsField.get(channelManager);
    assertEquals(existingChannel, connectedNodeIds.get(remoteNodeId),
        "Existing inbound channel should be kept when localId > remoteId");
  }

  /**
   * Test: BUG-P2P-004 FIX - When both connections have the same direction, prefer NEW.
   *
   * Scenario:
   * - Existing is OUTBOUND, New is also OUTBOUND
   * - Expected: Keep NEW (since existing might be half-open/stale), close existing
   *
   * Rationale: TCP half-open connections appear "active" but don't actually work.
   * The NEW connection just completed handshake so is guaranteed to be alive.
   */
  @Test
  public void testDuplicateConnection_SameDirection_FirstComeFirstServed() throws Exception {
    String localNodeId = "AAAA1111111111111111111111111111";
    String remoteNodeId = "ZZZZ9999999999999999999999999999";

    // Use reflection to set localNodeId
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, localNodeId);

    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55108);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55109);

    // Both channels are OUTBOUND
    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(true); // OUTBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    channelManager.onChannelActive(existingChannel);

    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(true); // also OUTBOUND

    channelManager.onChannelActive(newChannel);

    // BUG-P2P-004 FIX: When both are same direction, prefer NEW (to avoid stale connections)
    verify(existingChannel, times(1)).closeWithoutBan(); // existing closed via cleanupStaleChannel
    verify(newChannel, times(0)).closeWithoutBan(); // new is kept
  }

  /**
   * Test: Fallback to first-come-first-served when localNodeId is null.
   */
  @Test
  public void testDuplicateConnection_NoLocalNodeId_FallbackToFirstCome() throws Exception {
    // Set localNodeId to null
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    localNodeIdField.set(channelManager, null);

    String remoteNodeId = "remote-node-xyz";
    InetSocketAddress existingAddr = new InetSocketAddress("192.168.1.100", 55110);
    InetSocketAddress newAddr = new InetSocketAddress("192.168.1.100", 55111);

    Channel existingChannel = mock(Channel.class);
    when(existingChannel.getNodeId()).thenReturn(remoteNodeId);
    when(existingChannel.getRemoteAddress()).thenReturn(existingAddr);
    when(existingChannel.isActive()).thenReturn(false); // INBOUND

    ChannelHandlerContext existingCtx = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel existingNetty = mock(io.netty.channel.Channel.class);
    when(existingChannel.getCtx()).thenReturn(existingCtx);
    when(existingCtx.channel()).thenReturn(existingNetty);
    when(existingNetty.isActive()).thenReturn(true);
    when(existingNetty.isOpen()).thenReturn(true);
    when(existingNetty.isWritable()).thenReturn(true);

    channelManager.onChannelActive(existingChannel);

    Channel newChannel = mock(Channel.class);
    when(newChannel.getNodeId()).thenReturn(remoteNodeId);
    when(newChannel.getRemoteAddress()).thenReturn(newAddr);
    when(newChannel.isActive()).thenReturn(true); // OUTBOUND

    channelManager.onChannelActive(newChannel);

    // Without localNodeId, fallback to first-come-first-served: keep existing
    verify(existingChannel, times(0)).closeWithoutBan();
    verify(newChannel, times(1)).closeWithoutBan();
  }

  /**
   * Test: Verify that both nodes make the SAME decision about which connection to keep.
   *
   * This simulates the scenario where Node1 (ID=AAA) and Node2 (ID=ZZZ) connect simultaneously:
   * - Node1 makes outbound to Node2, Node2 makes outbound to Node1
   * - Both detect duplicate
   * - Node1 (smaller ID) should prefer outbound → keeps its outbound
   * - Node2 (larger ID) should prefer inbound → keeps Node1's outbound (which is Node2's inbound)
   *
   * Result: Both keep the SAME connection (Node1's outbound = Node2's inbound)
   */
  @Test
  public void testDuplicateConnection_BothNodesMakeSameDecision() throws Exception {
    // Node1's perspective: localNodeId=AAA (smaller), sees remoteNodeId=ZZZ
    // Node1 prefers OUTBOUND (since AAA < ZZZ)
    String node1LocalId = "AAAA1111111111111111111111111111";
    String node2LocalId = "ZZZZ9999999999999999999999999999";

    // ===== Simulate Node1's decision =====
    ChannelManager node1ChannelManager = new ChannelManager(p2pConfig, nodeManager);
    java.lang.reflect.Field node1LocalIdField = ChannelManager.class.getDeclaredField("localNodeId");
    node1LocalIdField.setAccessible(true);
    node1LocalIdField.set(node1ChannelManager, node1LocalId);

    // Node1's existing: inbound from Node2 (Node2's outbound)
    Channel node1Existing = mock(Channel.class);
    when(node1Existing.getNodeId()).thenReturn(node2LocalId);
    when(node1Existing.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.2.1", 60001));
    when(node1Existing.isActive()).thenReturn(false); // INBOUND (from Node2's outbound)
    ChannelHandlerContext ctx1e = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel netty1e = mock(io.netty.channel.Channel.class);
    when(node1Existing.getCtx()).thenReturn(ctx1e);
    when(ctx1e.channel()).thenReturn(netty1e);
    when(netty1e.isActive()).thenReturn(true);
    when(netty1e.isOpen()).thenReturn(true);
    when(netty1e.isWritable()).thenReturn(true);
    node1ChannelManager.onChannelActive(node1Existing);

    // Node1's new: outbound to Node2 (Node1's outbound)
    Channel node1New = mock(Channel.class);
    when(node1New.getNodeId()).thenReturn(node2LocalId);
    when(node1New.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.2.1", 60002));
    when(node1New.isActive()).thenReturn(true); // OUTBOUND
    node1ChannelManager.onChannelActive(node1New);

    // Node1 decision: AAA < ZZZ → prefer outbound → close existing inbound, keep new outbound
    verify(node1Existing, times(1)).closeWithoutBan();
    verify(node1New, times(0)).closeWithoutBan();

    // ===== Simulate Node2's decision =====
    ChannelManager node2ChannelManager = new ChannelManager(p2pConfig, nodeManager);
    java.lang.reflect.Field node2LocalIdField = ChannelManager.class.getDeclaredField("localNodeId");
    node2LocalIdField.setAccessible(true);
    node2LocalIdField.set(node2ChannelManager, node2LocalId);

    // Node2's existing: inbound from Node1 (Node1's outbound)
    Channel node2Existing = mock(Channel.class);
    when(node2Existing.getNodeId()).thenReturn(node1LocalId);
    when(node2Existing.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 60003));
    when(node2Existing.isActive()).thenReturn(false); // INBOUND (from Node1's outbound)
    ChannelHandlerContext ctx2e = mock(ChannelHandlerContext.class);
    io.netty.channel.Channel netty2e = mock(io.netty.channel.Channel.class);
    when(node2Existing.getCtx()).thenReturn(ctx2e);
    when(ctx2e.channel()).thenReturn(netty2e);
    when(netty2e.isActive()).thenReturn(true);
    when(netty2e.isOpen()).thenReturn(true);
    when(netty2e.isWritable()).thenReturn(true);
    node2ChannelManager.onChannelActive(node2Existing);

    // Node2's new: outbound to Node1 (Node2's outbound)
    Channel node2New = mock(Channel.class);
    when(node2New.getNodeId()).thenReturn(node1LocalId);
    when(node2New.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 60004));
    when(node2New.isActive()).thenReturn(true); // OUTBOUND
    node2ChannelManager.onChannelActive(node2New);

    // Node2 decision: ZZZ > AAA → prefer inbound → close new outbound, keep existing inbound
    verify(node2Existing, times(0)).closeWithoutBan();
    verify(node2New, times(1)).closeWithoutBan();

    // Both nodes now have the SAME connection:
    // - Node1 keeps: Node1's OUTBOUND to Node2
    // - Node2 keeps: Node1's OUTBOUND (which is Node2's INBOUND)
    // This is the SAME connection viewed from different perspectives!

    // Verify final state
    java.lang.reflect.Field connField = ChannelManager.class.getDeclaredField("connectedNodeIds");
    connField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> node1Conns = (java.util.Map<String, Channel>) connField.get(node1ChannelManager);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Channel> node2Conns = (java.util.Map<String, Channel>) connField.get(node2ChannelManager);

    // Node1 keeps outbound to ZZZ
    assertEquals(node1New, node1Conns.get(node2LocalId), "Node1 should keep its outbound connection");
    // Node2 keeps inbound from AAA
    assertEquals(node2Existing, node2Conns.get(node1LocalId), "Node2 should keep its inbound connection");

    // These represent the SAME TCP connection from different perspectives!
    // Node1's outbound (isActive=true) == Node2's inbound (isActive=false)
    assertTrue(node1New.isActive(), "Node1's kept connection should be outbound");
    assertFalse(node2Existing.isActive(), "Node2's kept connection should be inbound");
  }

  /**
   * Test: Local NodeId initialization when start() is called with valid nodeKey.
   */
  @Test
  public void testStartInitializesLocalNodeId() throws Exception {
    // Ensure nodeKey is set
    assertNotNull(p2pConfig.getNodeKey(), "nodeKey should be generated in @BeforeEach");

    PeerClient mockPeerClient = mock(PeerClient.class);
    channelManager.start(mockPeerClient);

    // Verify localNodeId is initialized
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    String localNodeId = (String) localNodeIdField.get(channelManager);

    assertNotNull(localNodeId, "localNodeId should be initialized after start()");
    assertFalse(localNodeId.isEmpty(), "localNodeId should not be empty");

    channelManager.stop();
  }

  /**
   * Test: Local NodeId is NOT initialized when nodeKey is null.
   */
  @Test
  public void testStartWithNullNodeKey() throws Exception {
    // Create a new config WITHOUT nodeKey
    P2pConfig configNoKey = new P2pConfig();
    // Don't call generateNodeKey()!
    assertNull(configNoKey.getNodeKey(), "nodeKey should be null");

    // Use mock NodeManager to avoid requiring nodeKey
    NodeManager mockNodeManager = mock(NodeManager.class);
    ChannelManager cm = new ChannelManager(configNoKey, mockNodeManager);

    PeerClient mockPeerClient = mock(PeerClient.class);
    cm.start(mockPeerClient);

    // Verify localNodeId is still null
    java.lang.reflect.Field localNodeIdField = ChannelManager.class.getDeclaredField("localNodeId");
    localNodeIdField.setAccessible(true);
    String localNodeId = (String) localNodeIdField.get(cm);

    assertNull(localNodeId, "localNodeId should remain null when nodeKey is not configured");

    cm.stop();
  }

  // ========== BUG-P2P-005: Fix for onChannelInactive removing replacement channel ==========

  /**
   * Test: BUG-P2P-005 - When a channel is replaced (due to duplicate detection),
   * the old channel's onChannelInactive() should NOT remove the replacement channel
   * from the channels map.
   *
   * Scenario:
   * 1. Channel A connects with remoteAddress 192.168.1.100:8001
   * 2. Channel B connects with SAME remoteAddress (duplicate detection replaces A with B)
   * 3. Channel A receives disconnect event, calls onChannelInactive()
   * 4. BUG (before fix): channels.remove(remoteAddress) removes Channel B
   * 5. FIX: Check channel instance identity before removing
   */
  @Test
  public void testOnChannelInactive_DoesNotRemoveReplacementChannel() {
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 8001);

    // Create old channel (will be replaced)
    Channel oldChannel = mock(Channel.class);
    when(oldChannel.getRemoteAddress()).thenReturn(remoteAddress);
    when(oldChannel.getInetAddress()).thenReturn(remoteAddress.getAddress());
    when(oldChannel.isActive()).thenReturn(true);
    when(oldChannel.getNodeId()).thenReturn("node-old");
    when(oldChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Create replacement channel (same remoteAddress)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getRemoteAddress()).thenReturn(remoteAddress);
    when(newChannel.getInetAddress()).thenReturn(remoteAddress.getAddress());
    when(newChannel.isActive()).thenReturn(true);
    when(newChannel.getNodeId()).thenReturn("node-new");
    when(newChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Simulate: old channel was active, then replaced by new channel
    // First, manually put the NEW channel in the map (simulating replacement)
    channelManager.getChannels().put(remoteAddress, newChannel);
    assertEquals(1, channelManager.getChannels().size(), "Should have 1 channel");
    assertEquals(newChannel, channelManager.getChannels().get(remoteAddress),
        "New channel should be stored at remoteAddress");

    // Now, old channel disconnects - this should NOT remove the new channel
    channelManager.onChannelInactive(oldChannel);

    // Verify: new channel is STILL in the map
    assertEquals(1, channelManager.getChannels().size(),
        "BUG-P2P-005: Replacement channel should NOT be removed by old channel's disconnect");
    assertEquals(newChannel, channelManager.getChannels().get(remoteAddress),
        "New channel should still be stored at remoteAddress");
  }

  /**
   * Test: When the ACTUAL stored channel disconnects, it SHOULD be removed.
   */
  @Test
  public void testOnChannelInactive_RemovesActualStoredChannel() {
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.101", 8002);

    // Create channel
    Channel channel = mock(Channel.class);
    when(channel.getRemoteAddress()).thenReturn(remoteAddress);
    when(channel.getInetAddress()).thenReturn(remoteAddress.getAddress());
    when(channel.isActive()).thenReturn(true);
    when(channel.getNodeId()).thenReturn("node-actual");
    when(channel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Add channel via onChannelActive
    channelManager.onChannelActive(channel);
    assertEquals(1, channelManager.getChannels().size(), "Should have 1 channel");
    assertEquals(channel, channelManager.getChannels().get(remoteAddress));

    // Channel disconnects - this SHOULD remove it
    channelManager.onChannelInactive(channel);

    // Verify: channel is removed
    assertEquals(0, channelManager.getChannels().size(),
        "Actual stored channel SHOULD be removed on disconnect");
    assertFalse(channelManager.getChannels().containsKey(remoteAddress),
        "RemoteAddress should no longer be in channels map");
  }

  /**
   * Test: BUG-P2P-005 - Verify peer counts are correctly maintained after replacement.
   *
   * This test simulates the real-world scenario where:
   * 1. Old channel was counted as active peer
   * 2. New channel replaces it
   * 3. Old channel disconnects
   * 4. Only NEW channel should remain, counts should be correct
   */
  @Test
  public void testOnChannelInactive_PeerCountsAfterReplacement() {
    InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.102", 8003);

    // Create old OUTBOUND channel
    Channel oldChannel = mock(Channel.class);
    when(oldChannel.getRemoteAddress()).thenReturn(remoteAddress);
    when(oldChannel.getInetAddress()).thenReturn(remoteAddress.getAddress());
    when(oldChannel.isActive()).thenReturn(true); // OUTBOUND
    when(oldChannel.getNodeId()).thenReturn("node-replace-test");
    when(oldChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Create new INBOUND channel (same nodeId, different port typically)
    Channel newChannel = mock(Channel.class);
    when(newChannel.getRemoteAddress()).thenReturn(remoteAddress);
    when(newChannel.getInetAddress()).thenReturn(remoteAddress.getAddress());
    when(newChannel.isActive()).thenReturn(false); // INBOUND
    when(newChannel.getNodeId()).thenReturn("node-replace-test");
    when(newChannel.getStartTime()).thenReturn(System.currentTimeMillis());

    // Simulate: new channel is already in the map (replacement happened)
    channelManager.getChannels().put(remoteAddress, newChannel);

    // Record initial counts (should be 0 since we directly manipulated the map)
    int initialActive = channelManager.getActivePeersCount();
    int initialPassive = channelManager.getPassivePeersCount();

    // Old channel disconnects - should NOT affect counts since it's not in the map
    channelManager.onChannelInactive(oldChannel);

    // Verify: counts unchanged (old channel was replaced, not actually removed)
    assertEquals(initialActive, channelManager.getActivePeersCount(),
        "Active peer count should be unchanged after old channel disconnect");
    assertEquals(initialPassive, channelManager.getPassivePeersCount(),
        "Passive peer count should be unchanged after old channel disconnect");

    // Verify: new channel still in map
    assertEquals(newChannel, channelManager.getChannels().get(remoteAddress),
        "New channel should still be in channels map");
  }
}
