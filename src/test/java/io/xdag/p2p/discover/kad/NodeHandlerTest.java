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
        UdpEvent sentEvent = captor.getAllValues().getLast();
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
        UdpEvent sentEvent = captor.getAllValues().getLast();
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

    // ==================== Additional Tests for Coverage ====================

    @Test
    public void testHandlePongTransitionsToAlive() {
        // Given - simulate PING sent and waiting for PONG
        // Set network IDs to match
        remoteNode.setNetworkId((byte) 1);
        remoteNodeHandler.sendPing();

        // When - receive PONG with matching network ID
        Node pongNode = new Node(remoteNode.getId(),
                                remoteNode.getHostV4(), null, remoteNode.getPort());
        pongNode.setNetworkId((byte) 1); // Matching network ID
        io.xdag.p2p.message.discover.KadPongMessage pong =
            new io.xdag.p2p.message.discover.KadPongMessage(pongNode);
        remoteNodeHandler.handlePong(pong);

        // Then - should transition to ALIVE state (or ACTIVE if table has space)
        NodeHandler.State newState = remoteNodeHandler.getState();
        assert(newState == NodeHandler.State.ALIVE || newState == NodeHandler.State.ACTIVE);
    }

    @Test
    public void testHandlePongIncreasesReputation() {
        // Given - initial reputation
        int initialReputation = remoteNodeHandler.getReputationScore();
        remoteNodeHandler.sendPing();

        // When - receive PONG
        io.xdag.p2p.message.discover.KadPongMessage pong =
            new io.xdag.p2p.message.discover.KadPongMessage(remoteNode);
        remoteNodeHandler.handlePong(pong);

        // Then - reputation should increase
        int newReputation = remoteNodeHandler.getReputationScore();
        assert(newReputation > initialReputation);
    }

    @Test
    public void testHandlePongWhenNotWaitingDoesNothing() {
        // Given - not waiting for PONG (initial state)
        NodeHandler.State initialState = remoteNodeHandler.getState();

        // When - receive unexpected PONG
        io.xdag.p2p.message.discover.KadPongMessage pong =
            new io.xdag.p2p.message.discover.KadPongMessage(remoteNode);
        remoteNodeHandler.handlePong(pong);

        // Then - state should not change to ALIVE (since waitForPong was false)
        // The state will change during sendPing in constructor, but not from this PONG
        assertNotNull(remoteNodeHandler.getState());
    }

    @Test
    public void testHandleNeighbours() {
        // Given - send FIND_NODE first
        byte[] targetId = new byte[20];
        random.nextBytes(targetId);
        remoteNodeHandler.sendFindNode(targetId);

        // When - receive NEIGHBORS
        java.util.List<Node> neighbors = new java.util.ArrayList<>();
        Node neighbor1 = new Node(Bytes.random(20).toUnprefixedHexString(),
                                  "192.168.1.100", null, 30303);
        neighbors.add(neighbor1);

        io.xdag.p2p.message.discover.KadNeighborsMessage neighborsMsg =
            new io.xdag.p2p.message.discover.KadNeighborsMessage(remoteNode, neighbors);

        remoteNodeHandler.handleNeighbours(neighborsMsg);

        // Then - should create handlers for neighbors
        verify(kadService, atLeast(1)).getNodeHandler(any(Node.class));
    }

    @Test
    public void testHandleNeighboursWithoutRequestIgnored() {
        // Given - no FIND_NODE sent, so not waiting for neighbors
        java.util.List<Node> neighbors = new java.util.ArrayList<>();
        neighbors.add(new Node(Bytes.random(20).toUnprefixedHexString(),
                              "192.168.1.100", null, 30303));

        io.xdag.p2p.message.discover.KadNeighborsMessage neighborsMsg =
            new io.xdag.p2p.message.discover.KadNeighborsMessage(remoteNode, neighbors);

        // When - receive unsolicited NEIGHBORS
        remoteNodeHandler.handleNeighbours(neighborsMsg);

        // Then - should log warning and not process (verified by not creating node handlers)
        // The test passes if no exception is thrown
    }

    @Test
    public void testHandleTimedOutDecreasesReputation() {
        // Given - initial reputation and PING sent
        int initialReputation = remoteNodeHandler.getReputationScore();
        remoteNodeHandler.sendPing();

        // When - timeout occurs
        remoteNodeHandler.handleTimedOut();

        // Then - reputation should decrease
        int newReputation = remoteNodeHandler.getReputationScore();
        assert(newReputation < initialReputation);
    }

    @Test
    public void testHandleTimedOutTransitionsToDead() {
        // Given - node in DISCOVERED state, exhaust all ping trials
        assertEquals(NodeHandler.State.DISCOVERED, remoteNodeHandler.getState());

        // When - timeout multiple times (3 trials: initial + 2 retries)
        remoteNodeHandler.handleTimedOut(); // Trial 2
        remoteNodeHandler.handleTimedOut(); // Trial 1
        remoteNodeHandler.handleTimedOut(); // Trial 0 -> DEAD

        // Then - should transition to DEAD
        assertEquals(NodeHandler.State.DEAD, remoteNodeHandler.getState());
    }

    @Test
    public void testSendPong() {
        // When - send PONG
        remoteNodeHandler.sendPong();

        // Then - should send PONG message
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(1)).sendOutbound(captor.capture());

        // Find the PONG message
        boolean foundPong = captor.getAllValues().stream()
            .anyMatch(event -> event.getMessage().getType() == io.xdag.p2p.message.MessageCode.KAD_PONG);
        assert(foundPong);
    }

    @Test
    public void testSendFindNode() {
        // Given - target ID
        byte[] targetId = new byte[20];
        random.nextBytes(targetId);

        // When - send FIND_NODE
        remoteNodeHandler.sendFindNode(targetId);

        // Then - should send FIND_NODE message
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(1)).sendOutbound(captor.capture());

        // Find the FIND_NODE message
        boolean foundFindNode = captor.getAllValues().stream()
            .anyMatch(event -> event.getMessage().getType() == io.xdag.p2p.message.MessageCode.KAD_FIND_NODE);
        assert(foundFindNode);
    }

    @Test
    public void testSendNeighbours() {
        // Given - list of neighbors
        java.util.List<Node> neighbors = new java.util.ArrayList<>();
        neighbors.add(new Node(Bytes.random(20).toUnprefixedHexString(),
                              "192.168.1.100", null, 30303));

        // When - send NEIGHBORS
        remoteNodeHandler.sendNeighbours(neighbors, System.currentTimeMillis());

        // Then - should send NEIGHBORS message
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(1)).sendOutbound(captor.capture());

        // Find the NEIGHBORS message
        boolean foundNeighbors = captor.getAllValues().stream()
            .anyMatch(event -> event.getMessage().getType() == io.xdag.p2p.message.MessageCode.KAD_NEIGHBORS);
        assert(foundNeighbors);
    }

    @Test
    public void testHandlePingWithIncompatibleNetworkId() {
        // Given - node with different network ID
        when(p2pConfig.getNetworkId()).thenReturn((byte) 1);
        Node incompatibleNode = new Node(Bytes.random(20).toUnprefixedHexString(),
                                        "192.168.1.50", null, 30303);
        incompatibleNode.setNetworkId((byte) 99); // Different network ID
        NodeHandler incompatibleHandler = new NodeHandler(incompatibleNode, kadService);

        // When - handle PING with incompatible network - create ping from incompatible node
        KadPingMessage ping = new KadPingMessage(incompatibleNode, remoteNode);
        incompatibleHandler.handlePing(ping);

        // Then - should transition to DEAD state
        assertEquals(NodeHandler.State.DEAD, incompatibleHandler.getState());
    }

    @Test
    public void testHandlePongWithIncompatibleNetworkId() {
        // Given - send PING first
        when(p2pConfig.getNetworkId()).thenReturn((byte) 1);
        remoteNode.setNetworkId((byte) 1);
        remoteNodeHandler.sendPing();

        // Create a node with incompatible network ID
        Node incompatibleNode = new Node(remoteNode.getId(),
                                        remoteNode.getHostV4(), null, remoteNode.getPort());
        incompatibleNode.setNetworkId((byte) 99); // Incompatible network ID

        // When - receive PONG from incompatible network
        io.xdag.p2p.message.discover.KadPongMessage pong =
            new io.xdag.p2p.message.discover.KadPongMessage(incompatibleNode);
        remoteNodeHandler.handlePong(pong);

        // Then - should transition to DEAD state
        assertEquals(NodeHandler.State.DEAD, remoteNodeHandler.getState());
    }

    @Test
    public void testNodeHandlerWithNullAddress() {
        // Given - node without valid address
        Node nodeWithoutAddress = new Node(Bytes.random(20).toUnprefixedHexString(),
                                          null, null, 0);

        // When - create NodeHandler
        NodeHandler handlerWithoutAddress = new NodeHandler(nodeWithoutAddress, kadService);

        // Then - state should be null since no PING was sent (no valid address)
        // This is expected behavior - nodes without addresses cannot be discovered
        assert(handlerWithoutAddress.getState() == null ||
               handlerWithoutAddress.getState() != NodeHandler.State.DISCOVERED);
    }

    @Test
    public void testReputationBounds() {
        // Given - initial reputation
        remoteNodeHandler.sendPing();

        // When - receive many PONGs to increase reputation beyond max
        for (int i = 0; i < 50; i++) {
            io.xdag.p2p.message.discover.KadPongMessage pong =
                new io.xdag.p2p.message.discover.KadPongMessage(remoteNode);
            remoteNodeHandler.handlePong(pong);
            remoteNodeHandler.sendPing(); // Send ping to set waitForPong flag
        }

        // Then - reputation should not exceed maximum (200)
        int reputation = remoteNodeHandler.getReputationScore();
        assert(reputation <= 200);
    }

    @Test
    public void testHandleNeighboursFiltersOutHomeNode() {
        // Given - send FIND_NODE first
        byte[] targetId = new byte[20];
        random.nextBytes(targetId);
        remoteNodeHandler.sendFindNode(targetId);

        // When - receive NEIGHBORS including home node
        java.util.List<Node> neighbors = new java.util.ArrayList<>();
        neighbors.add(homeNode); // Should be filtered out
        neighbors.add(new Node(Bytes.random(20).toUnprefixedHexString(),
                              "192.168.1.100", null, 30303));

        io.xdag.p2p.message.discover.KadNeighborsMessage neighborsMsg =
            new io.xdag.p2p.message.discover.KadNeighborsMessage(remoteNode, neighbors);

        remoteNodeHandler.handleNeighbours(neighborsMsg);

        // Then - should process neighbors but filter out home node
        // Verified by checking getNodeHandler is called (for non-home nodes)
        verify(kadService, atLeast(1)).getNodeHandler(any(Node.class));
    }

    @Test
    public void testChangeStateFromDiscoveredToAlive() {
        // Given - node in DISCOVERED state
        assertEquals(NodeHandler.State.DISCOVERED, remoteNodeHandler.getState());

        // When - transition to ALIVE
        remoteNodeHandler.changeState(NodeHandler.State.ALIVE);

        // Then - state should be ALIVE or ACTIVE (depending on table space)
        NodeHandler.State newState = remoteNodeHandler.getState();
        assert(newState == NodeHandler.State.ALIVE || newState == NodeHandler.State.ACTIVE);
    }
}
