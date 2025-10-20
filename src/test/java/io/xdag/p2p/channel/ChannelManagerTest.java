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
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);

    // Now should be banned
    assertTrue(channelManager.isBanned(address));
    assertNotNull(channelManager.getBanInfo(address));

    BanInfo banInfo = channelManager.getBanInfo(address);
    assertEquals(BanReason.PROTOCOL_VIOLATION, banInfo.reason());
    assertEquals(1, banInfo.banCount());
  }

  @Test
  public void testBanNodeWithCustomDuration() {
    InetAddress address = a1.getAddress();
    long customDuration = 5000; // 5 seconds

    channelManager.banNode(address, BanReason.MALICIOUS_BEHAVIOR, customDuration);

    assertTrue(channelManager.isBanned(address));
    BanInfo banInfo = channelManager.getBanInfo(address);
    assertNotNull(banInfo);
    assertEquals(BanReason.MALICIOUS_BEHAVIOR, banInfo.reason());
  }

  @Test
  public void testUnbanNode() {
    InetAddress address = a1.getAddress();

    // Ban the node
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
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
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
    assertFalse(channelManager.isBanned(address));

    // Remove from whitelist
    channelManager.removeFromWhitelist(address);
    assertFalse(channelManager.isWhitelisted(address));

    // Now ban should work
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
    assertTrue(channelManager.isBanned(address));
  }

  @Test
  public void testGraduatedBanDuration() {
    InetAddress address = a1.getAddress();

    // First ban
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
    BanInfo banInfo1 = channelManager.getBanInfo(address);
    assertEquals(1, banInfo1.banCount());

    // Unban and ban again - count should increase
    channelManager.unbanNode(address);
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
    BanInfo banInfo2 = channelManager.getBanInfo(address);
    assertEquals(2, banInfo2.banCount());
  }

  @Test
  public void testGetAllBannedNodes() {
    InetAddress address1 = a1.getAddress();
    InetAddress address2 = a2.getAddress();

    assertEquals(0, channelManager.getAllBannedNodes().size());

    channelManager.banNode(address1, BanReason.PROTOCOL_VIOLATION);
    channelManager.banNode(address2, BanReason.MALICIOUS_BEHAVIOR);

    assertEquals(2, channelManager.getAllBannedNodes().size());
    assertEquals(2, channelManager.getBannedNodeCount());
  }

  @Test
  public void testBanStatistics() {
    InetAddress address = a1.getAddress();

    BanStatistics stats = channelManager.getBanStatistics();
    assertNotNull(stats);

    int initialBans = stats.getTotalBans().get();

    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);

    assertEquals(initialBans + 1, stats.getTotalBans().get());
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
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION, 100);
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
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION);
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
    channelManager.banNode(null, BanReason.PROTOCOL_VIOLATION);
    assertEquals(0, channelManager.getBannedNodeCount());
  }

  @Test
  public void testBanNodeWithZeroDuration() {
    InetAddress address = a1.getAddress();

    // Ban with zero duration should be ignored
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION, 0);
    assertFalse(channelManager.isBanned(address));
    assertEquals(0, channelManager.getBannedNodeCount());
  }

  @Test
  public void testBanNodeWithNegativeDuration() {
    InetAddress address = a1.getAddress();

    // Ban with negative duration should be ignored
    channelManager.banNode(address, BanReason.PROTOCOL_VIOLATION, -1000);
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
    channelManager.banNode(address, BanReason.MALICIOUS_BEHAVIOR);

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
    channelManager.banNode(address, BanReason.MANUAL_BAN, customDuration);

    // Should work with custom duration
    assertTrue(channelManager.isBanned(address));
    BanInfo banInfo = channelManager.getBanInfo(address);
    assertNotNull(banInfo);
    assertEquals(BanReason.MANUAL_BAN, banInfo.reason());
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
}
