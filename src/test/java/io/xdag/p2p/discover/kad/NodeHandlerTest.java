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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.NodeTable;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadNeighborsMessage;
import io.xdag.p2p.message.discover.KadPacket;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * One node as seen by discovery: it is verified by a pong that echoes our ping, and only then trusted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeHandlerTest {

    private static final byte NETWORK = 1;

    @Mock private KadService kadService;
    @Mock private P2pConfig p2pConfig;
    @Mock private ScheduledExecutorService pongTimer;

    private Node homeNode;
    private Node remoteNode;
    private NodeHandler handler;

    private static String id(int seed) {
        return Bytes.wrap(new byte[]{(byte) seed}).toHexString().replace("0x", "0x" + "00".repeat(19));
    }

    @BeforeEach
    public void setUp() {
        homeNode = new Node(id(1), "127.0.0.1", null, 10001);
        homeNode.setNetworkId(NETWORK);
        remoteNode = new Node(id(2), "127.0.0.2", null, 10002);
        remoteNode.setNetworkId(NETWORK);

        when(kadService.getP2pConfig()).thenReturn(p2pConfig);
        when(kadService.getPublicHomeNode()).thenReturn(homeNode);
        when(kadService.getTable()).thenReturn(new NodeTable(homeNode, true));
        when(kadService.mayContact(any(Node.class))).thenReturn(true);
        when(p2pConfig.getNetworkId()).thenReturn(NETWORK);
        when(p2pConfig.isPermissionless()).thenReturn(true);
        when(kadService.getPongTimer()).thenReturn(pongTimer);
        when(pongTimer.isShutdown()).thenReturn(false);
        when(pongTimer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenReturn(null);

        handler = new NodeHandler(remoteNode, kadService);
        handler.start();
    }

    private List<UdpEvent> sent() {
        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService, atLeast(0)).sendOutbound(captor.capture());
        return captor.getAllValues();
    }

    private UdpEvent last() {
        List<UdpEvent> all = sent();
        return all.isEmpty() ? null : all.getLast();
    }

    /** Answers the ping we last sent, the way the node at that endpoint would. */
    private KadPongMessage answerToOurPing() {
        KadPingMessage ping = (KadPingMessage) last().getMessage();
        return new KadPongMessage(remoteNode, KadPacket.hashOf(ping, NETWORK));
    }

    @Test
    public void startSendsAPing() {
        assertEquals(NodeHandler.State.DISCOVERED, handler.getState());
        UdpEvent event = last();
        assertNotNull(event);
        assertEquals(remoteNode.getPreferInetSocketAddress(), event.getAddress());
        assertEquals(MessageCode.KAD_PING, event.getMessage().getCode());
        assertFalse(handler.isVerified());
    }

    @Test
    public void pingIsAnsweredWithAPongThatEchoesIt() {
        KadPingMessage ping = new KadPingMessage(remoteNode, homeNode);
        Bytes32 hash = Bytes32.random();
        handler.handlePing(ping, hash);
        UdpEvent event = last();
        assertEquals(MessageCode.KAD_PONG, event.getMessage().getCode());
        assertEquals(hash, ((KadPongMessage) event.getMessage()).getEcho());
        assertEquals(remoteNode.getPreferInetSocketAddress(), event.getAddress());
    }

    @Test
    public void pongThatEchoesOurPingVerifiesTheNode() {
        handler.handlePong(answerToOurPing());
        assertTrue(handler.isVerified());
        NodeHandler.State state = handler.getState();
        assertTrue(state == NodeHandler.State.ALIVE || state == NodeHandler.State.ACTIVE);
        assertTrue(kadService.getTable().contains(remoteNode));
        assertTrue(handler.getReputationScore() > 100);
    }

    @Test
    public void pongThatEchoesNothingOfOursIsIgnored() {
        handler.handlePong(new KadPongMessage(remoteNode, Bytes32.random()));
        assertFalse(handler.isVerified());
        assertEquals(NodeHandler.State.DISCOVERED, handler.getState());
        assertFalse(kadService.getTable().contains(remoteNode));
        assertEquals(100, handler.getReputationScore());
    }

    @Test
    public void pongWhenNotWaitingIsIgnored() {
        KadPongMessage pong = answerToOurPing();
        handler.handlePong(pong);
        assertTrue(handler.isVerified());
        int reputation = handler.getReputationScore();
        handler.handlePong(pong); // the same pong again
        assertEquals(reputation, handler.getReputationScore());
    }

    @Test
    public void findNodeIsOnlyAnsweredForVerifiedNodes() {
        KadFindNodeMessage find = new KadFindNodeMessage(remoteNode, Bytes.random(KadFindNodeMessage.TARGET_LENGTH));
        int before = sent().size();
        handler.handleFindNode(find);
        assertEquals(before, sent().size(), "an unverified node gets no neighbours");

        handler.handlePong(answerToOurPing());
        handler.handleFindNode(find);
        UdpEvent event = last();
        assertEquals(MessageCode.KAD_NEIGHBORS, event.getMessage().getCode());
        for (Node n : ((KadNeighborsMessage) event.getMessage()).getNeighbors()) {
            assertFalse(n.equals(remoteNode), "the asker is not told about itself");
        }
    }

    @Test
    public void neighboursAreContactedOnlyWhenAskedFor() {
        Node other = new Node(id(3), "10.1.1.1", null, 30303);
        other.setNetworkId(NETWORK);
        KadNeighborsMessage msg = new KadNeighborsMessage(remoteNode, List.of(other));

        handler.handleNeighbours(msg);
        verify(kadService, never()).getNodeHandler(any(Node.class));

        handler.sendFindNode(new byte[KadFindNodeMessage.TARGET_LENGTH]);
        handler.handleNeighbours(msg);
        verify(kadService).getNodeHandler(any(Node.class));
    }

    @Test
    public void neighboursThatMayNotBeContactedAreDropped() {
        when(kadService.mayContact(any(Node.class))).thenReturn(false);
        Node other = new Node(id(3), "10.1.1.1", null, 30303);
        handler.sendFindNode(new byte[KadFindNodeMessage.TARGET_LENGTH]);
        handler.handleNeighbours(new KadNeighborsMessage(remoteNode, List.of(other, homeNode)));
        verify(kadService, never()).getNodeHandler(any(Node.class));
    }

    @Test
    public void nodeOfAnotherNetworkIsVerifiedButKeptOutOfTheTable() {
        remoteNode.setNetworkId((byte) 9);
        KadPingMessage ping = (KadPingMessage) last().getMessage();
        Node from = new Node(id(2), "127.0.0.2", null, 10002);
        from.setNetworkId((byte) 9);
        handler.handlePong(new KadPongMessage(from, KadPacket.hashOf(ping, NETWORK)));
        assertTrue(handler.isVerified());
        assertFalse(kadService.getTable().contains(remoteNode));
        assertFalse(handler.getState() == NodeHandler.State.ACTIVE);
    }

    @Test
    public void timeoutsRetryThenGiveUp() {
        int reputation = handler.getReputationScore();
        handler.handleTimedOut();
        assertTrue(handler.getReputationScore() < reputation);
        assertEquals(NodeHandler.State.DISCOVERED, handler.getState());
        handler.handleTimedOut();
        handler.handleTimedOut();
        assertEquals(NodeHandler.State.DEAD, handler.getState());
    }

    @Test
    public void neighboursAreBoundedWhenSent() {
        List<Node> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(new Node(id(10 + i), "10.0.0." + (i + 1), null, 30303));
        }
        int before = sent().size();
        handler.sendNeighbours(many, 0);
        List<UdpEvent> events = sent().subList(before, sent().size());
        int total = 0;
        for (UdpEvent e : events) {
            KadNeighborsMessage msg = (KadNeighborsMessage) e.getMessage();
            assertTrue(msg.getNeighbors().size() <= NodeHandler.NEIGHBORS_PER_PACKET);
            assertTrue(KadPacket.encode(msg, NETWORK, io.xdag.crypto.keys.ECKeyPair.generate()).size() <= KadPacket.MAX_LENGTH,
                    "every datagram fits");
            total += msg.getNeighbors().size();
        }
        assertEquals(KadNeighborsMessage.MAX_NEIGHBORS, total, "a bucket's worth, no more");
    }

    @Test
    public void answersInSeveralDatagramsAreAllAccepted() {
        handler.sendFindNode(new byte[KadFindNodeMessage.TARGET_LENGTH]);
        List<Node> part1 = new ArrayList<>();
        List<Node> part2 = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            part1.add(new Node(id(20 + i), "10.0.1." + (i + 1), null, 30303));
            part2.add(new Node(id(40 + i), "10.0.2." + (i + 1), null, 30303));
        }
        handler.handleNeighbours(new KadNeighborsMessage(remoteNode, part1));
        handler.handleNeighbours(new KadNeighborsMessage(remoteNode, part2));
        verify(kadService, org.mockito.Mockito.times(16)).getNodeHandler(any(Node.class));
        // a bucket's worth has arrived: a third datagram is not an answer any more
        handler.handleNeighbours(new KadNeighborsMessage(remoteNode, part1));
        verify(kadService, org.mockito.Mockito.times(16)).getNodeHandler(any(Node.class));
    }

    @Test
    public void handlerWithoutAddressSendsNothing() {
        Node noAddress = new Node(id(4), null, null, 10003);
        NodeHandler h = new NodeHandler(noAddress, kadService);
        int before = sent().size();
        h.start();
        assertEquals(before, sent().size());
        assertNull(noAddress.getPreferInetSocketAddress());
    }

    @Test
    public void reputationStaysWithinBounds() {
        for (int i = 0; i < 100; i++) {
            handler.handleTimedOut();
        }
        assertTrue(handler.getReputationScore() >= 0);
        for (int i = 0; i < 100; i++) {
            handler.sendPing();
            handler.handlePong(answerToOurPing());
        }
        assertTrue(handler.getReputationScore() <= 200);
    }

    @Test
    public void toStringMentionsState() {
        assertTrue(handler.toString().contains("state"));
    }
}
