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
import org.apache.tuweni.bytes.Bytes32;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ==================== signed events ====================

    private static final byte NETWORK = 2;

    /** What the packet decoder produces for a datagram signed by {@code key} and sent from {@code from}. */
    private static UdpEvent signed(io.xdag.p2p.message.Message message, InetSocketAddress from,
            io.xdag.crypto.keys.ECKeyPair key, byte networkId) {
        io.xdag.p2p.message.discover.KadPacket packet;
        try {
            packet = io.xdag.p2p.message.discover.KadPacket.decode(
                    io.xdag.p2p.message.discover.KadPacket.encode(message, networkId, key), networkId);
        } catch (io.xdag.p2p.message.MessageException e) {
            throw new AssertionError(e);
        }
        return new UdpEvent(packet.getMessage(), from, packet.getNodeId(), packet.getHash());
    }

    private Node nodeOf(io.xdag.crypto.keys.ECKeyPair key, String host, int port) {
        Node n = new Node(key.toAddress().toHexString(), host, null, port);
        n.setNetworkId(p2pConfig.getNetworkId());
        n.setNetworkVersion(p2pConfig.getNetworkVersion());
        return n;
    }

    private List<UdpEvent> outbound(KadService service) {
        List<UdpEvent> events = new ArrayList<>();
        p2pConfig.setDiscoverEnable(true); // sending is gated on it; the discovery task was not started
        service.setMessageSender(events::add);
        return events;
    }

    @Test
    public void pingFromAStrangerIsAnsweredAndTheStrangerIsPingedBack() {
        p2pConfig.setAllowPrivateAddresses(true);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22222);
        Node claimed = nodeOf(key, "8.8.8.8", 22222); // what it says about its address is not used

        kadService.handleEvent(signed(new KadPingMessage(claimed, homeNode), from, key, p2pConfig.getNetworkId()));

        // the stranger is pinged back (verification) and its ping is answered, both where the datagram came from
        assertEquals(2, out.size());
        assertEquals(MessageCode.KAD_PING, out.get(0).getMessage().getCode());
        assertEquals(from, out.get(0).getAddress());
        assertEquals(MessageCode.KAD_PONG, out.get(1).getMessage().getCode());
        assertEquals(from, out.get(1).getAddress(), "answered where the datagram came from");
        Node tracked = kadService.getAllNodes().getFirst();
        assertEquals(key.toAddress().toHexString(), tracked.getId(), "identified by the key that signed");
        assertEquals("127.0.0.1", tracked.getHostV4());
        assertTrue(kadService.getConnectableNodes().isEmpty() || kadService.getConnectableNodes().equals(kadService.getBootNodes()),
                "not connectable until verified");
    }

    @Test
    public void pongVerifiesTheEndpointAndMakesItConnectable() {
        p2pConfig.setAllowPrivateAddresses(true);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22223);
        Node remote = nodeOf(key, "127.0.0.1", 22223);

        NodeHandler handler = kadService.getNodeHandler(remote);
        assertNotNull(handler);
        KadPingMessage ourPing = (KadPingMessage) out.getLast().getMessage();
        Bytes32 echo = io.xdag.p2p.message.discover.KadPacket.hashOf(ourPing, p2pConfig.getNetworkId());

        // a pong with the wrong echo proves nothing
        kadService.handleEvent(signed(new KadPongMessage(remote, Bytes32.random()), from, key, p2pConfig.getNetworkId()));
        assertFalse(handler.isVerified());

        kadService.handleEvent(signed(new KadPongMessage(remote, echo), from, key, p2pConfig.getNetworkId()));
        assertTrue(handler.isVerified());
        assertTrue(kadService.getConnectableNodes().stream().anyMatch(n -> key.toAddress().toHexString().equals(n.getId())));
    }

    @Test
    public void findNodeFromAnUnverifiedSenderIsNotAnswered() {
        p2pConfig.setAllowPrivateAddresses(true);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22224);
        Node remote = nodeOf(key, "127.0.0.1", 22224);
        KadFindNodeMessage find = new KadFindNodeMessage(remote, Bytes.random(KadFindNodeMessage.TARGET_LENGTH));

        kadService.handleEvent(signed(find, from, key, p2pConfig.getNetworkId()));
        assertTrue(out.stream().noneMatch(e -> e.getMessage().getCode() == MessageCode.KAD_NEIGHBORS));
    }

    @Test
    public void eventsWithoutASignerOrFromAnotherNetworkAreDropped() {
        p2pConfig.setAllowPrivateAddresses(true);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22225);
        Node remote = nodeOf(key, "127.0.0.1", 22225);

        kadService.handleEvent(new UdpEvent(new KadPingMessage(remote, homeNode), from));
        assertTrue(out.isEmpty(), "an event without a verified sender is ignored");
        assertTrue(kadService.getAllNodes().isEmpty());
    }

    @Test
    public void staleEventsAreDropped() throws Exception {
        p2pConfig.setAllowPrivateAddresses(true);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22226);
        Node remote = nodeOf(key, "127.0.0.1", 22226);
        KadPingMessage old = new KadPingMessage(remote, homeNode);
        java.lang.reflect.Field ts = KadPingMessage.class.getDeclaredField("timestamp");
        ts.setAccessible(true);
        ts.set(old, System.currentTimeMillis() - 10 * KadService.MAX_CLOCK_SKEW_MS);
        java.lang.reflect.Field body = io.xdag.p2p.message.Message.class.getDeclaredField("body");
        body.setAccessible(true);
        io.xdag.p2p.utils.SimpleEncoder enc = new io.xdag.p2p.utils.SimpleEncoder();
        old.encode(enc);
        body.set(old, enc.toBytes());

        kadService.handleEvent(signed(old, from, key, p2pConfig.getNetworkId()));
        assertTrue(out.isEmpty());
    }

    @Test
    public void aNewKeyAtAKnownEndpointIsANewNode() {
        p2pConfig.setAllowPrivateAddresses(true);
        outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair first = io.xdag.crypto.keys.ECKeyPair.generate();
        io.xdag.crypto.keys.ECKeyPair second = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22227);

        kadService.handleEvent(signed(new KadPingMessage(nodeOf(first, "127.0.0.1", 22227), homeNode), from, first, p2pConfig.getNetworkId()));
        kadService.handleEvent(signed(new KadPingMessage(nodeOf(second, "127.0.0.1", 22227), homeNode), from, second, p2pConfig.getNetworkId()));
        assertEquals(1, kadService.getAllNodes().size());
        assertEquals(second.toAddress().toHexString(), kadService.getAllNodes().getFirst().getId());
    }

    @Test
    public void privateAddressesFromOthersAreNotContactedUnlessAllowed() {
        p2pConfig.setAllowPrivateAddresses(false);
        Node privateNode = nodeOf(io.xdag.crypto.keys.ECKeyPair.generate(), "192.168.1.9", 30303);
        assertFalse(kadService.mayContact(privateNode));
        Node publicNode = nodeOf(io.xdag.crypto.keys.ECKeyPair.generate(), "8.8.8.8", 30303);
        assertTrue(kadService.mayContact(publicNode));
        Node named = new Node(publicNode.getId(), "example.invalid", null, 30303);
        assertFalse(kadService.mayContact(named), "names are never resolved");
        assertFalse(kadService.mayContact(homeNode));
    }

    @Test
    public void rateLimitDropsFloods() {
        p2pConfig.setAllowPrivateAddresses(true);
        p2pConfig.setMaxDiscoveryBurst(3);
        p2pConfig.setMaxDiscoveryPacketsPerSecond(1);
        List<UdpEvent> out = outbound(kadService);
        io.xdag.crypto.keys.ECKeyPair key = io.xdag.crypto.keys.ECKeyPair.generate();
        InetSocketAddress from = new InetSocketAddress("127.0.0.1", 22228);
        Node remote = nodeOf(key, "127.0.0.1", 22228);
        for (int i = 0; i < 10; i++) {
            kadService.handleEvent(signed(new KadPingMessage(remote, homeNode), from, key, p2pConfig.getNetworkId()));
        }
        long pongs = out.stream().filter(e -> e.getMessage().getCode() == MessageCode.KAD_PONG).count();
        assertEquals(3, pongs, "only the burst is answered");
    }
}
