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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class to verify the ROOT CAUSE of connection flapping issue.
 *
 * <p><b>Root Cause:</b> Boot nodes are created with NULL IDs (see KadService.java lines 82-83, 89-90).
 * This causes the first guard check in connectLoop to fail:
 * <pre>
 * if (node.getId() != null && connectedNodeIds.containsKey(node.getId())) {
 *     // This check fails because node.getId() is null!
 * }
 * </pre>
 *
 * <p><b>Connection Flapping Scenario:</b>
 * <ol>
 *   <li>Node1 has boot node entry for Node2 with null ID, address = 127.0.0.1:8002</li>
 *   <li>Node2 connects to Node1 (INBOUND), seen from 127.0.0.1:[ephemeral port]</li>
 *   <li>Handshake completes, channel stored with Node2's actual nodeId</li>
 *   <li>30 seconds pass, recentConnections cache expires</li>
 *   <li>connectLoop runs:
 *     <ul>
 *       <li>Gets Node2 boot node (null ID) from getConnectableNodes()</li>
 *       <li>Check `node.getId() != null` fails (null ID)</li>
 *       <li>Check `hasActiveConnectionTo(127.0.0.1:8002)` - might fail because existing connection is 127.0.0.1:[ephemeral]</li>
 *       <li>New connection initiated!</li>
 *     </ul>
 *   </li>
 *   <li>Duplicate detected, one connection closed, activePeers count changes</li>
 *   <li>Cycle repeats every 30 seconds</li>
 * </ol>
 */
public class ConnectionFlappingRootCauseTest {

    private P2pConfig p2pConfig;
    private ChannelManager channelManager;
    private NodeManager nodeManager;

    @BeforeEach
    public void setUp() {
        p2pConfig = new P2pConfig();
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

    /**
     * Test that demonstrates boot nodes have NULL IDs.
     *
     * <p>This is the foundation of the connection flapping bug.
     */
    @Test
    public void testBootNodesHaveNullId() {
        // Simulate how KadService creates boot nodes
        InetSocketAddress bootNodeAddress = new InetSocketAddress("127.0.0.1", 8002);

        // This is exactly what KadService does (lines 82-83, 89-90)
        Node bootNode = new Node(null, bootNodeAddress);
        bootNode.setNetworkId((byte) 2);
        bootNode.setNetworkVersion((short) 0);

        // VERIFY: Boot node has null ID
        assertNull(bootNode.getId(),
                "ROOT CAUSE PROOF: Boot nodes are created with NULL ID!");

        // This is why the first guard check fails
        boolean firstGuardWouldSkip = bootNode.getId() != null; // This is FALSE!
        assertFalse(firstGuardWouldSkip,
                "First guard check (node.getId() != null) returns FALSE for boot nodes!");
    }

    /**
     * Test hasActiveConnectionTo behavior with loopback address and different ports.
     *
     * <p>Scenario: We have an INBOUND connection from 127.0.0.1:[ephemeral port],
     * but we want to check if we have connection to 127.0.0.1:8002 (the boot node's listening port).
     *
     * <p>Expected: Should return TRUE because:
     * <ul>
     *   <li>Target is loopback address</li>
     *   <li>We have an active connection from same IP with valid nodeId</li>
     * </ul>
     */
    @Test
    public void testHasActiveConnectionTo_LoopbackDifferentPorts() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod(
                "hasActiveConnectionTo", InetSocketAddress.class);
        method.setAccessible(true);

        java.lang.reflect.Field connectedNodeIdsField =
                ChannelManager.class.getDeclaredField("connectedNodeIds");
        connectedNodeIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Channel> connectedNodeIds =
                (Map<String, Channel>) connectedNodeIdsField.get(channelManager);

        // Target: Boot node's listening address (what connectLoop wants to connect to)
        InetSocketAddress targetAddress = new InetSocketAddress("127.0.0.1", 8002);

        // Existing connection: INBOUND from ephemeral port (what we actually have)
        InetSocketAddress existingAddress = new InetSocketAddress("127.0.0.1", 54321);
        String nodeId = "node2-actual-id-from-handshake";

        // Create mock channel representing the existing INBOUND connection
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getRemoteAddress()).thenReturn(existingAddress);
        when(mockChannel.getNodeId()).thenReturn(nodeId);

        ChannelHandlerContext mockCtx = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
        when(mockChannel.getCtx()).thenReturn(mockCtx);
        when(mockCtx.channel()).thenReturn(mockNettyChannel);
        when(mockNettyChannel.isActive()).thenReturn(true);

        // Add to connectedNodeIds (simulating post-handshake state)
        connectedNodeIds.put(nodeId, mockChannel);

        // TEST: Does hasActiveConnectionTo recognize the connection?
        boolean result = (boolean) method.invoke(channelManager, targetAddress);

        // EXPECTED: TRUE (because of loopback IP match + nodeId check)
        // If this fails, it explains why new connections are being attempted!
        assertTrue(result,
                "hasActiveConnectionTo should return TRUE for loopback with different port " +
                "when an active connection exists with valid nodeId");
    }

    /**
     * Test the CRITICAL check path that's failing.
     *
     * <p>This test simulates the exact check sequence in connectLoop (lines 390-401):
     * <pre>
     * // Skip if already connected to this Node ID (prevents duplicate connections)
     * if (node.getId() != null && connectedNodeIds.containsKey(node.getId())) {
     *     continue;  // FAILS for boot nodes because node.getId() is null!
     * }
     *
     * // Skip if we already have an active connection to this address
     * if (hasActiveConnectionTo(address)) {
     *     continue;  // MAY FAIL because address doesn't match!
     * }
     * </pre>
     */
    @Test
    public void testConnectLoopGuardChecks() throws Exception {
        java.lang.reflect.Method hasActiveMethod = ChannelManager.class.getDeclaredMethod(
                "hasActiveConnectionTo", InetSocketAddress.class);
        hasActiveMethod.setAccessible(true);

        java.lang.reflect.Field connectedNodeIdsField =
                ChannelManager.class.getDeclaredField("connectedNodeIds");
        connectedNodeIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Channel> connectedNodeIds =
                (Map<String, Channel>) connectedNodeIdsField.get(channelManager);

        // === SETUP: Simulate existing INBOUND connection ===
        String actualNodeId = "actual-node2-id-base58check-format";
        InetSocketAddress inboundAddress = new InetSocketAddress("127.0.0.1", 55555); // Ephemeral port

        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getRemoteAddress()).thenReturn(inboundAddress);
        when(mockChannel.getNodeId()).thenReturn(actualNodeId);

        ChannelHandlerContext mockCtx = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
        when(mockChannel.getCtx()).thenReturn(mockCtx);
        when(mockCtx.channel()).thenReturn(mockNettyChannel);
        when(mockNettyChannel.isActive()).thenReturn(true);

        connectedNodeIds.put(actualNodeId, mockChannel);

        // === TEST: Boot node from getConnectableNodes() ===
        InetSocketAddress bootNodeAddress = new InetSocketAddress("127.0.0.1", 8002);
        Node bootNode = new Node(null, bootNodeAddress); // NULL ID!

        // Check 1: node.getId() != null && connectedNodeIds.containsKey(node.getId())
        boolean check1Passes;
        if (bootNode.getId() != null && connectedNodeIds.containsKey(bootNode.getId())) {
            check1Passes = true; // Would skip connection
        } else {
            check1Passes = false; // Would NOT skip
        }

        // VERIFY: Check 1 FAILS because boot node has null ID!
        assertFalse(check1Passes,
                "Guard Check 1 FAILS for boot nodes because getId() returns null!");

        // Check 2: hasActiveConnectionTo(bootNodeAddress)
        boolean check2Passes = (boolean) hasActiveMethod.invoke(channelManager, bootNodeAddress);

        // Check 2 should pass IF loopback handling works correctly
        // This is the second line of defense
        assertTrue(check2Passes,
                "Guard Check 2 (hasActiveConnectionTo) should return TRUE for loopback addresses " +
                "with valid nodeId in connectedNodeIds");

        // If both checks fail, a new connection would be initiated!
        boolean connectionWouldBeAttempted = !check1Passes && !check2Passes;
        assertFalse(connectionWouldBeAttempted,
                "Connection should NOT be attempted when we already have an active connection!");
    }

    /**
     * Test that verifies the loopback detection relies on nodeId being set.
     *
     * <p>If the channel has no nodeId, the loopback check returns false.
     */
    @Test
    public void testLoopbackCheckRequiresNodeId() throws Exception {
        java.lang.reflect.Method method = ChannelManager.class.getDeclaredMethod(
                "hasActiveConnectionTo", InetSocketAddress.class);
        method.setAccessible(true);

        java.lang.reflect.Field connectedNodeIdsField =
                ChannelManager.class.getDeclaredField("connectedNodeIds");
        connectedNodeIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Channel> connectedNodeIds =
                (Map<String, Channel>) connectedNodeIdsField.get(channelManager);

        InetSocketAddress targetAddress = new InetSocketAddress("127.0.0.1", 8002);
        InetSocketAddress existingAddress = new InetSocketAddress("127.0.0.1", 54321);

        // Channel WITHOUT nodeId
        Channel mockChannelNoId = mock(Channel.class);
        when(mockChannelNoId.getRemoteAddress()).thenReturn(existingAddress);
        when(mockChannelNoId.getNodeId()).thenReturn(null); // NO nodeId!

        ChannelHandlerContext mockCtx = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel mockNettyChannel = mock(io.netty.channel.Channel.class);
        when(mockChannelNoId.getCtx()).thenReturn(mockCtx);
        when(mockCtx.channel()).thenReturn(mockNettyChannel);
        when(mockNettyChannel.isActive()).thenReturn(true);

        // Add with some key (though normally this wouldn't happen with null nodeId)
        connectedNodeIds.put("temp-key", mockChannelNoId);

        // TEST: hasActiveConnectionTo should return FALSE
        boolean result = (boolean) method.invoke(channelManager, targetAddress);

        assertFalse(result,
                "hasActiveConnectionTo should return FALSE for loopback when channel has no nodeId");
    }

    /**
     * Comprehensive test simulating the exact connection flapping scenario.
     *
     * <p>This test proves that with current code:
     * 1. Boot node with null ID bypasses first guard
     * 2. Second guard (hasActiveConnectionTo) DOES protect us IF implemented correctly
     * 3. Therefore the fix is NOT about changing duplicate detection, but about
     *    ensuring hasActiveConnectionTo works correctly for loopback addresses
     */
    @Test
    public void testConnectionFlappingScenario() throws Exception {
        java.lang.reflect.Method hasActiveMethod = ChannelManager.class.getDeclaredMethod(
                "hasActiveConnectionTo", InetSocketAddress.class);
        hasActiveMethod.setAccessible(true);

        java.lang.reflect.Field connectedNodeIdsField =
                ChannelManager.class.getDeclaredField("connectedNodeIds");
        connectedNodeIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Channel> connectedNodeIds =
                (Map<String, Channel>) connectedNodeIdsField.get(channelManager);

        // === STEP 1: Node2 connects to Node1 (INBOUND connection) ===
        String node2Id = "node2-base58-encoded-id";
        InetSocketAddress node2InboundAddr = new InetSocketAddress("127.0.0.1", 49876);

        Channel inboundChannel = mock(Channel.class);
        when(inboundChannel.getRemoteAddress()).thenReturn(node2InboundAddr);
        when(inboundChannel.getNodeId()).thenReturn(node2Id);
        when(inboundChannel.isActive()).thenReturn(false); // INBOUND

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        io.netty.channel.Channel nettyChannel = mock(io.netty.channel.Channel.class);
        when(inboundChannel.getCtx()).thenReturn(ctx);
        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.isActive()).thenReturn(true);
        when(nettyChannel.isOpen()).thenReturn(true);
        when(nettyChannel.isWritable()).thenReturn(true);

        // Handshake completes, channel added to connectedNodeIds
        connectedNodeIds.put(node2Id, inboundChannel);
        channelManager.getChannels().put(node2InboundAddr, inboundChannel);

        // === STEP 2: 30 seconds later, connectLoop runs ===
        // Node1 has boot node entry for Node2
        InetSocketAddress node2ListenAddr = new InetSocketAddress("127.0.0.1", 8002);
        Node bootNode = new Node(null, node2ListenAddr); // NULL ID (this is the bug source)

        // Guard Check 1: Fails because bootNode.getId() is null
        boolean skipByNodeId = bootNode.getId() != null &&
                               connectedNodeIds.containsKey(bootNode.getId());
        assertFalse(skipByNodeId, "First guard fails for boot nodes");

        // Guard Check 2: Should succeed (this is the safety net)
        boolean skipByActiveConnection = (boolean) hasActiveMethod.invoke(
                channelManager, bootNode.getPreferInetSocketAddress());

        // If this assertion passes, the connection flapping should NOT occur
        // If this assertion fails, we found where hasActiveConnectionTo fails!
        assertTrue(skipByActiveConnection,
                "Second guard (hasActiveConnectionTo) should protect against duplicate connection " +
                "for loopback addresses with valid nodeId in connectedNodeIds. " +
                "If this fails, the loopback detection in hasActiveConnectionTo() is not working!");
    }
}
