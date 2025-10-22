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
package io.xdag.p2p.discover.kad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.discover.KadPingMessage;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class KadServiceTest {

    private P2pConfig p2pConfig;
    private KadService kadService;

    private Node homeNode;
    private Node remoteNode;

    @BeforeEach
    public void setUp() {
        p2pConfig = new P2pConfig();
        p2pConfig.setDiscoverEnable(false); // Disable discovery task for unit tests

        // Generate nodeKey for testing - required for node ID generation
        p2pConfig.generateNodeKey();

        kadService = new KadService(p2pConfig);
        kadService.init();

        homeNode = kadService.getPublicHomeNode();

        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 22222);
        String remoteId = Bytes.random(20).toUnprefixedHexString();
        remoteNode = new Node(remoteId, remoteAddress);
    }

    @AfterEach
    public void tearDown() {
        if (kadService != null) {
            kadService.close();
        }
    }

    @Test
    public void testInit() {
        assertNotNull(kadService.getPublicHomeNode());
        assertNotNull(kadService.getTable());
        assertEquals(0, kadService.getBootNodes().size()); // No bootnodes in default config
    }

    @Test
    public void testGetNodeHandlerNew() {
        NodeHandler handler = kadService.getNodeHandler(remoteNode);
        assertNotNull(handler);
        assertEquals(1, kadService.getAllNodes().size());
        assertEquals(remoteNode, handler.getNode());
    }

    @Test
    public void testGetNodeHandlerExisting() {
        NodeHandler handler1 = kadService.getNodeHandler(remoteNode);
        // Ensure the same node object is passed to get the same handler
        NodeHandler handler2 = kadService.getNodeHandler(remoteNode);
        assertSame(handler1, handler2, "Should return the same handler instance for the same node");
        assertEquals(1, kadService.getAllNodes().size());
    }

    @Test
    public void testHandleEventPing() {
        // Spy on the real KadService to verify internal method calls
        KadService spiedService = spy(kadService);
        NodeHandler mockNodeHandler = mock(NodeHandler.class);

        doAnswer(invocation -> {
            Node arg = invocation.getArgument(0);
            assertEquals(remoteNode.getId(), arg.getId());
            return mockNodeHandler;
        }).when(spiedService).getNodeHandler(any(Node.class));

        when(mockNodeHandler.getNode()).thenReturn(remoteNode);

        KadPingMessage ping = new KadPingMessage(remoteNode, homeNode);
        UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

        spiedService.handleEvent(event);

        verify(mockNodeHandler).handlePing(ping);
        verify(mockNodeHandler).getNode();
    }

    @Test
    public void testSendOutbound() {
        // Mock the message sender
        @SuppressWarnings("unchecked")
        Consumer<UdpEvent> sender = mock(Consumer.class);
        kadService.setMessageSender(sender);
        kadService.getP2pConfig().setDiscoverEnable(true); // Enable discovery for this test

        KadPingMessage ping = new KadPingMessage(homeNode, remoteNode);
        UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

        kadService.sendOutbound(event);

        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(sender).accept(captor.capture());
        assertEquals(event, captor.getValue());
    }

    @Test
    public void testSendOutboundDisabled() {
        // Mock the message sender
        @SuppressWarnings("unchecked")
        Consumer<UdpEvent> sender = mock(Consumer.class);
        kadService.setMessageSender(sender);
        kadService.getP2pConfig().setDiscoverEnable(false); // Ensure discovery is disabled

        KadPingMessage ping = new KadPingMessage(homeNode, remoteNode);
        UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

        kadService.sendOutbound(event);

        // Verify sender was not called
        verify(sender, never()).accept(any());
    }

    @Test
    public void testChannelActivated() throws Exception {
        // Set up bootnodes
        List<InetSocketAddress> bootnodeAddresses = new ArrayList<>();
        bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30301));
        bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30302));
        p2pConfig.setSeedNodes(bootnodeAddresses);

        // Re-initialize KadService to pick up the new config
        kadService = new KadService(p2pConfig);
        kadService.init();

        assertEquals(2, kadService.getBootNodes().size());
        Map<InetSocketAddress, NodeHandler> before = getNodeHandlerMap();
        int sizeBefore = before.size();

        kadService.channelActivated();

        Map<InetSocketAddress, NodeHandler> after = getNodeHandlerMap();
        assertTrue(kadService.isInited());
        assertTrue(after.size() >= sizeBefore);
    }

    @SuppressWarnings("unchecked")
    private Map<InetSocketAddress, NodeHandler> getNodeHandlerMap() throws Exception {
        Field mapField = KadService.class.getDeclaredField("nodeHandlerMap");
        mapField.setAccessible(true);
        return (Map<InetSocketAddress, NodeHandler>) mapField.get(kadService);
    }

    // ==================== Additional Tests for Coverage ====================

    @Test
    public void testHandleEventPong() {
        KadService spiedService = spy(kadService);
        NodeHandler mockNodeHandler = mock(NodeHandler.class);

        doAnswer(invocation -> mockNodeHandler).when(spiedService).getNodeHandler(any(Node.class));
        when(mockNodeHandler.getNode()).thenReturn(remoteNode);

        io.xdag.p2p.message.discover.KadPongMessage pong =
            new io.xdag.p2p.message.discover.KadPongMessage(remoteNode);
        UdpEvent event = new UdpEvent(pong, remoteNode.getInetSocketAddressV4());

        spiedService.handleEvent(event);

        verify(mockNodeHandler).handlePong(pong);
        verify(mockNodeHandler).getNode();
    }

    @Test
    public void testHandleEventFindNode() {
        KadService spiedService = spy(kadService);
        NodeHandler mockNodeHandler = mock(NodeHandler.class);

        doAnswer(invocation -> mockNodeHandler).when(spiedService).getNodeHandler(any(Node.class));
        when(mockNodeHandler.getNode()).thenReturn(remoteNode);

        Bytes targetId = Bytes.fromHexString(homeNode.getId());
        io.xdag.p2p.message.discover.KadFindNodeMessage findNode =
            new io.xdag.p2p.message.discover.KadFindNodeMessage(remoteNode, targetId);
        UdpEvent event = new UdpEvent(findNode, remoteNode.getInetSocketAddressV4());

        spiedService.handleEvent(event);

        verify(mockNodeHandler).handleFindNode(findNode);
        verify(mockNodeHandler).getNode();
    }

    @Test
    public void testHandleEventNeighbors() {
        KadService spiedService = spy(kadService);
        NodeHandler mockNodeHandler = mock(NodeHandler.class);

        doAnswer(invocation -> mockNodeHandler).when(spiedService).getNodeHandler(any(Node.class));
        when(mockNodeHandler.getNode()).thenReturn(remoteNode);

        List<Node> neighbors = new ArrayList<>();
        neighbors.add(new Node(Bytes.random(20).toUnprefixedHexString(),
                               new InetSocketAddress("127.0.0.1", 33333)));

        io.xdag.p2p.message.discover.KadNeighborsMessage neighborsMsg =
            new io.xdag.p2p.message.discover.KadNeighborsMessage(remoteNode, neighbors);
        UdpEvent event = new UdpEvent(neighborsMsg, remoteNode.getInetSocketAddressV4());

        spiedService.handleEvent(event);

        verify(mockNodeHandler).handleNeighbours(neighborsMsg);
        verify(mockNodeHandler).getNode();
    }

    @Test
    public void testGetConnectableNodesWithDiscoveredNodes() {
        // Create a discovered node with a node handler
        NodeHandler handler = kadService.getNodeHandler(remoteNode);
        assertNotNull(handler);

        List<Node> connectable = kadService.getConnectableNodes();

        assertTrue(connectable.size() >= 1, "Should have at least one connectable node");
        assertTrue(connectable.stream().anyMatch(n -> n.getId().equals(remoteNode.getId())),
                   "Should include discovered node");
    }

    @Test
    public void testGetConnectableNodesWithBootNodesOnly() throws Exception {
        // Create a fresh KadService with bootnodes
        P2pConfig configWithBoot = new P2pConfig();
        configWithBoot.setDiscoverEnable(false);
        configWithBoot.generateNodeKey();

        List<InetSocketAddress> bootnodeAddresses = new ArrayList<>();
        bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30301));
        configWithBoot.setSeedNodes(bootnodeAddresses);

        KadService serviceWithBoot = new KadService(configWithBoot);
        serviceWithBoot.init();

        List<Node> connectable = serviceWithBoot.getConnectableNodes();

        assertEquals(1, connectable.size(), "Should fallback to bootnodes");

        serviceWithBoot.close();
    }

    @Test
    public void testGetTableNodes() {
        // Add a node to the table via NodeHandler
        kadService.getNodeHandler(remoteNode);

        List<Node> tableNodes = kadService.getTableNodes();

        assertNotNull(tableNodes, "Table nodes should not be null");
        // Table nodes depend on NodeTable's addNode logic
    }

    @Test
    public void testGetAllNodes() {
        assertEquals(0, kadService.getAllNodes().size(), "Should start with no nodes");

        kadService.getNodeHandler(remoteNode);

        assertEquals(1, kadService.getAllNodes().size(), "Should have one node");

        Node anotherNode = new Node(Bytes.random(20).toUnprefixedHexString(),
                                     new InetSocketAddress("127.0.0.1", 44444));
        kadService.getNodeHandler(anotherNode);

        assertEquals(2, kadService.getAllNodes().size(), "Should have two nodes");
    }

    @Test
    public void testSetMessageSender() {
        @SuppressWarnings("unchecked")
        Consumer<UdpEvent> sender = mock(Consumer.class);

        kadService.setMessageSender(sender);
        kadService.getP2pConfig().setDiscoverEnable(true);

        KadPingMessage ping = new KadPingMessage(homeNode, remoteNode);
        UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

        kadService.sendOutbound(event);

        verify(sender).accept(event);
    }

    @Test
    public void testGetPublicHomeNode() {
        Node home = kadService.getPublicHomeNode();

        assertNotNull(home, "Home node should not be null");
        assertNotNull(home.getId(), "Home node should have an ID");
        // Node ID is hex string (with 0x prefix) of XDAG address (20 bytes = 0x + 40 hex chars)
        assertEquals(42, home.getId().length(), "Node ID should be 42 chars (0x + 40 hex)");
    }

    @Test
    public void testCloseMethod() {
        assertNotNull(kadService);

        // Should not throw exception
        kadService.close();

        // Calling close multiple times should be safe
        kadService.close();
    }

    @Test
    public void testNodeHandlerMapUpdatesWithIPv4AndIPv6() {
        // Create a node with both IPv4 and IPv6
        Node dualStackNode = new Node(Bytes.random(20).toUnprefixedHexString(),
                                      "192.168.1.100", "2001:db8::1", 30303);

        NodeHandler handler1 = kadService.getNodeHandler(dualStackNode);
        assertNotNull(handler1);

        // getNodeHandler with different address but same concept should still work
        // The handler tracks by address, not just ID, so creating with different
        // addresses will return the existing handler and update its node info
        Node sameAddressNode = new Node(Bytes.random(20).toUnprefixedHexString(), // Different ID
                                        "192.168.1.100", "2001:db8::1", 30303); // Same addresses

        NodeHandler handler2 = kadService.getNodeHandler(sameAddressNode);

        // Should return the same handler because address matches
        assertSame(handler1, handler2, "Should return same handler for same addresses");
    }

    @Test
    public void testHandleEventWithNodeInfoUpdate() {
        // Create a ping message with full node information
        Node fullInfoNode = new Node(Bytes.random(20).toUnprefixedHexString(),
                                     "10.0.0.1", "2001:db8::100", 30303);
        fullInfoNode.setNetworkId(p2pConfig.getNetworkId());
        fullInfoNode.setNetworkVersion(p2pConfig.getNetworkVersion());

        KadPingMessage ping = new KadPingMessage(fullInfoNode, homeNode);
        UdpEvent event = new UdpEvent(ping, fullInfoNode.getInetSocketAddressV4());

        kadService.handleEvent(event);

        // Verify the node was registered
        List<Node> allNodes = kadService.getAllNodes();
        assertTrue(allNodes.stream().anyMatch(n -> n.getId().equals(fullInfoNode.getId())),
                   "Node should be registered in the system");
    }

    @Test
    public void testChannelActivatedIdempotence() throws Exception {
        // Set up bootnodes
        List<InetSocketAddress> bootnodeAddresses = new ArrayList<>();
        bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30301));
        p2pConfig.setSeedNodes(bootnodeAddresses);

        KadService service = new KadService(p2pConfig);
        service.init();

        Map<InetSocketAddress, NodeHandler> beforeFirst = getNodeHandlerMapFor(service);
        int sizeBeforeFirst = beforeFirst.size();

        service.channelActivated();
        Map<InetSocketAddress, NodeHandler> afterFirst = getNodeHandlerMapFor(service);

        // Call channelActivated again
        service.channelActivated();
        Map<InetSocketAddress, NodeHandler> afterSecond = getNodeHandlerMapFor(service);

        // Second call should not create duplicate handlers
        assertEquals(afterFirst.size(), afterSecond.size(),
                    "Second channelActivated call should not create duplicates");

        service.close();
    }

    @Test
    public void testHandleEventWithNullNodeId() {
        // Create a node without an ID, but send from a valid node
        Node nodeWithoutId = new Node(null, new InetSocketAddress("127.0.0.1", 55555));
        nodeWithoutId.setNetworkId(p2pConfig.getNetworkId());
        nodeWithoutId.setNetworkVersion(p2pConfig.getNetworkVersion());

        // Create a valid ping message (from node must be valid)
        KadPingMessage ping = new KadPingMessage(nodeWithoutId, homeNode);
        UdpEvent event = new UdpEvent(ping, nodeWithoutId.getInetSocketAddressV4());

        // Should not throw exception even when node ID is null
        kadService.handleEvent(event);
    }

    @Test
    public void testInitWithBootNodesFromActiveNodes() {
        P2pConfig config = new P2pConfig();
        config.setDiscoverEnable(false);
        config.generateNodeKey();

        List<InetSocketAddress> activeNodes = new ArrayList<>();
        activeNodes.add(new InetSocketAddress("192.168.1.10", 16789));
        activeNodes.add(new InetSocketAddress("192.168.1.20", 16789));
        config.setActiveNodes(activeNodes);

        KadService service = new KadService(config);
        service.init();

        assertEquals(2, service.getBootNodes().size(), "Should have 2 active nodes as bootnodes");

        service.close();
    }

    @Test
    public void testInitWithSeedAndActiveNodes() {
        P2pConfig config = new P2pConfig();
        config.setDiscoverEnable(false);
        config.generateNodeKey();

        List<InetSocketAddress> seedNodes = new ArrayList<>();
        seedNodes.add(new InetSocketAddress("10.0.0.1", 30303));
        config.setSeedNodes(seedNodes);

        List<InetSocketAddress> activeNodes = new ArrayList<>();
        activeNodes.add(new InetSocketAddress("192.168.1.10", 16789));
        config.setActiveNodes(activeNodes);

        KadService service = new KadService(config);
        service.init();

        assertEquals(2, service.getBootNodes().size(), "Should have both seed and active nodes");

        service.close();
    }

    @SuppressWarnings("unchecked")
    private Map<InetSocketAddress, NodeHandler> getNodeHandlerMapFor(KadService service) throws Exception {
        Field mapField = KadService.class.getDeclaredField("nodeHandlerMap");
        mapField.setAccessible(true);
        return (Map<InetSocketAddress, NodeHandler>) mapField.get(service);
    }
}
