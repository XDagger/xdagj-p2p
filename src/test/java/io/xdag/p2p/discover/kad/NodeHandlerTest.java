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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadPingMessage;
import java.security.SecureRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeHandlerTest {

    @Mock private KadService kadService;
    @Mock private P2pConfig p2pConfig;
    @Mock private ScheduledExecutorService pongTimer;

    private Node homeNode;
    private Node remoteNode;
    private NodeHandler remoteNodeHandler;

    private final SecureRandom random = new SecureRandom();

    @BeforeEach
    public void setUp() {
        // Generate hex string IDs for nodes
        byte[] homeId = new byte[64];
        byte[] remoteId = new byte[64];
        random.nextBytes(homeId);
        random.nextBytes(remoteId);

        homeNode = new Node(Bytes.wrap(homeId).toHexString(), "127.0.0.1", null, 10001);
        remoteNode = new Node(Bytes.wrap(remoteId).toHexString(), "127.0.0.2", null, 10002);

        when(kadService.getP2pConfig()).thenReturn(p2pConfig);
        when(kadService.getPublicHomeNode()).thenReturn(homeNode);
        when(kadService.getTable()).thenReturn(new io.xdag.p2p.discover.kad.table.NodeTable(homeNode));
        when(p2pConfig.getNetworkId()).thenReturn((byte) 1);
        when(kadService.getPongTimer()).thenReturn(pongTimer);
        when(pongTimer.isShutdown()).thenReturn(false);
        when(pongTimer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenReturn(null);

        remoteNodeHandler = new NodeHandler(remoteNode, kadService);
    }

    @Test
    public void testHandlePing() {
        // Given
        KadPingMessage ping = new KadPingMessage(homeNode, remoteNode);

        // When
        remoteNodeHandler.handlePing(ping);

        // Then - verify latest message (there are 2: one from constructor, one from handlePing)
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(2)).sendOutbound(captor.capture());

        // Get the last sent event (the PONG response)
        UdpEvent sentEvent = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(remoteNode.getPreferInetSocketAddress(), sentEvent.getAddress());
        assertEquals(io.xdag.p2p.message.MessageCode.KAD_PONG, sentEvent.getMessage().getType());
    }

    @Test
    public void testHandleFindNode() {
        // Given
        byte[] targetId = new byte[64];
        random.nextBytes(targetId);
        KadFindNodeMessage findNode = new KadFindNodeMessage(homeNode, Bytes.wrap(targetId));

        // When
        remoteNodeHandler.handleFindNode(findNode);

        // Then - verify latest message (there are 2: one PING from constructor, one NEIGHBORS from handleFindNode)
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(2)).sendOutbound(captor.capture());

        // Get the last sent event (the NEIGHBORS response)
        UdpEvent sentEvent = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(remoteNode.getPreferInetSocketAddress(), sentEvent.getAddress());
        assertEquals(io.xdag.p2p.message.MessageCode.KAD_NEIGHBORS, sentEvent.getMessage().getType());
    }

    @Test
    public void testNodeHandlerCreation() {
        // Verify NodeHandler can be created successfully
        assertNotNull(remoteNodeHandler);
        assertNotNull(remoteNodeHandler.getNode());
        assertEquals(remoteNode, remoteNodeHandler.getNode());
        assertNotNull(remoteNodeHandler.getState());
    }

    @Test
    public void testGetReputationScore() {
        // Verify initial reputation score
        int initialReputation = remoteNodeHandler.getReputationScore();
        assertEquals(100, initialReputation, "Initial reputation should be 100 (neutral)");
    }

    @Test
    public void testSendPing() {
        // Given - NodeHandler is already created in setUp and sent initial PING

        // When - send another PING
        remoteNodeHandler.sendPing();

        // Then - should have sent at least 2 PINGs (1 from constructor, 1 from this call)
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(2)).sendOutbound(captor.capture());

        // Verify all sent messages are PINGs
        for (UdpEvent event : captor.getAllValues()) {
            assertEquals(remoteNode.getPreferInetSocketAddress(), event.getAddress());
            assertEquals(io.xdag.p2p.message.MessageCode.KAD_PING, event.getMessage().getType());
        }
    }

    @Test
    public void testStateTransitions() {
        // Verify initial state
        assertEquals(NodeHandler.State.DISCOVERED, remoteNodeHandler.getState(),
                     "New node should start in DISCOVERED state");
    }

    @Test
    public void testToString() {
        // Verify toString contains useful information
        String nodeHandlerString = remoteNodeHandler.toString();
        assertNotNull(nodeHandlerString);
        assert(nodeHandlerString.contains("NodeHandler"));
        assert(nodeHandlerString.contains("state"));
    }
}
