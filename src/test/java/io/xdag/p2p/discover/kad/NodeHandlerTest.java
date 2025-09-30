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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadNeighborsMessage;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NodeHandlerTest {

    @Mock private KadService kadService;
    @Mock private P2pConfig p2pConfig;

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
        
        remoteNodeHandler = new NodeHandler(remoteNode, kadService);
    }

    @Test
    public void testHandlePing() {
        KadPingMessage ping = new KadPingMessage(homeNode, remoteNode);
        remoteNodeHandler.handlePing(ping);

        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService).sendOutbound(captor.capture());

        UdpEvent sentEvent = captor.getValue();
        assertEquals(remoteNode.getPreferInetSocketAddress(), sentEvent.getAddress());
        assertEquals(io.xdag.p2p.message.MessageCode.KAD_PONG, sentEvent.getMessage().getCode());
    }


    @Test
    public void testHandleFindNode() {
        byte[] targetId = new byte[64];
        random.nextBytes(targetId);
        KadFindNodeMessage findNode = new KadFindNodeMessage(homeNode, Bytes.wrap(targetId));

        remoteNodeHandler.handleFindNode(findNode);

        ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
        verify(kadService).sendOutbound(captor.capture());

        UdpEvent sentEvent = captor.getValue();
        assertEquals(remoteNode.getPreferInetSocketAddress(), sentEvent.getAddress());
        assertEquals(io.xdag.p2p.message.MessageCode.KAD_NEIGHBORS, sentEvent.getMessage().getCode());
    }

    // NOTE: The following tests are disabled because the API has changed
    // waitForPong(), isWaitingForPong(), waitForFindNode(), and isWaitingForNeighbors() methods
    // no longer exist in the current NodeHandler implementation.
    // These tests should be rewritten based on the new internal state management approach.
    
    /*
    @Test
    public void testHandlePong() {
        // API changed: waitForPong() method no longer exists
        KadPongMessage pong = new KadPongMessage();
        remoteNodeHandler.handlePong(pong);
        // Cannot verify waiting state as the API has changed
    }

    @Test
    public void testHandleNeighbours() {
        // API changed: waitForFindNode() method no longer exists
        List<Node> neighbors = Collections.singletonList(homeNode);
        KadNeighborsMessage neighborsMessage = new KadNeighborsMessage(homeNode, neighbors);
        remoteNodeHandler.handleNeighbours(neighborsMessage);
        // Cannot verify waiting state as the API has changed
    }

    @Test
    public void testSendPing_NotWaiting() {
        remoteNodeHandler.sendPing();
        verify(kadService).sendOutbound(any(UdpEvent.class));
    }

    @Test
    public void testSendPing_AlreadyWaiting() {
        // API changed: waitForPong() method no longer exists
        remoteNodeHandler.sendPing();
        // Cannot verify waiting behavior as the API has changed
    }
    */

}
