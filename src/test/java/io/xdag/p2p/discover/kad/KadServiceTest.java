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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import io.prometheus.client.CollectorRegistry;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.metrics.P2pMetrics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

        P2pMetrics metrics = new P2pMetrics();
        kadService = new KadService(p2pConfig, metrics);
        kadService.init();

        homeNode = kadService.getPublicHomeNode();

        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 22222);
        String remoteId = Bytes.random(20).toUnprefixedHexString();
        remoteNode = new Node(remoteId, remoteAddress);
    }

    @AfterEach
    public void tearDown() {
        // Clear the Prometheus registry to avoid conflicts between tests
        CollectorRegistry.defaultRegistry.clear();
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

        // Clear registry before creating new metrics instance
        CollectorRegistry.defaultRegistry.clear();

        // Re-initialize KadService to pick up the new config
        P2pMetrics metrics = new P2pMetrics();
        kadService = new KadService(p2pConfig, metrics);
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
}
