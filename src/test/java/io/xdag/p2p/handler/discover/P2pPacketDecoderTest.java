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
package io.xdag.p2p.handler.discover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadNeighborsMessage;
import io.xdag.p2p.message.discover.KadPacket;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import io.xdag.p2p.message.node.PingMessage;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Datagram -&gt; event: only well-formed, correctly signed discovery messages of this network get through.
 */
class P2pPacketDecoderTest {

    private P2pConfig config;
    private EmbeddedChannel channel;
    private InetSocketAddress senderAddress;
    private InetSocketAddress localAddress;
    private ECKeyPair senderKey;
    private Node fromNode;
    private Node toNode;

    @BeforeEach
    void setUp() {
        config = new P2pConfig();
        config.setNetworkId((byte) 2);
        channel = new EmbeddedChannel(new P2pPacketDecoder(config));

        senderAddress = new InetSocketAddress("127.0.0.1", 8080);
        localAddress = new InetSocketAddress("0.0.0.0", 30303);

        senderKey = ECKeyPair.generate();
        fromNode = new Node(senderKey.toAddress().toHexString(), "192.168.1.100", null, 30303);
        toNode = new Node(ECKeyPair.generate().toAddress().toHexString(), "192.168.1.101", null, 30303);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private DatagramPacket packetOf(Bytes wire) {
        return new DatagramPacket(Unpooled.wrappedBuffer(wire.toArray()), localAddress, senderAddress);
    }

    private DatagramPacket signed(Message message) {
        return packetOf(KadPacket.encode(message, config.getNetworkId(), senderKey));
    }

    private UdpEvent decode(DatagramPacket packet) {
        channel.writeInbound(packet);
        return channel.readInbound();
    }

    @Test
    void testDecodeValidKadPingMessage() {
        UdpEvent event = decode(signed(new KadPingMessage(fromNode, toNode)));
        assertNotNull(event);
        assertInstanceOf(KadPingMessage.class, event.getMessage());
        assertEquals(MessageCode.KAD_PING, event.getMessage().getCode());
        assertEquals(senderAddress, event.getAddress());
        assertEquals(senderKey.toAddress().toHexString(), event.getNodeId(), "the signer is the sender");
        assertNotNull(event.getHash());
    }

    @Test
    void testDecodeValidKadPongMessage() {
        Bytes32 echo = Bytes32.random();
        UdpEvent event = decode(signed(new KadPongMessage(fromNode, echo)));
        assertNotNull(event);
        assertInstanceOf(KadPongMessage.class, event.getMessage());
        assertEquals(echo, ((KadPongMessage) event.getMessage()).getEcho());
    }

    @Test
    void testDecodeAllKadMessageTypes() {
        assertInstanceOf(KadPingMessage.class, decode(signed(new KadPingMessage(fromNode, toNode))).getMessage());
        assertInstanceOf(KadPongMessage.class, decode(signed(new KadPongMessage(fromNode, Bytes32.ZERO))).getMessage());
        assertInstanceOf(KadFindNodeMessage.class,
                decode(signed(new KadFindNodeMessage(fromNode, Bytes.random(KadFindNodeMessage.TARGET_LENGTH)))).getMessage());
        assertInstanceOf(KadNeighborsMessage.class,
                decode(signed(new KadNeighborsMessage(fromNode, List.of(toNode)))).getMessage());
    }

    @Test
    void testDecodeMultiplePackets() {
        for (int i = 0; i < 5; i++) {
            assertNotNull(decode(signed(new KadPingMessage(fromNode, toNode))));
        }
    }

    @Test
    void testDecodeDifferentSenderAddresses() {
        for (String host : new String[]{"127.0.0.1", "10.0.0.1", "8.8.8.8"}) {
            InetSocketAddress sender = new InetSocketAddress(host, 40000);
            channel.writeInbound(new DatagramPacket(
                    Unpooled.wrappedBuffer(KadPacket.encode(new KadPingMessage(fromNode, toNode), config.getNetworkId(), senderKey).toArray()),
                    localAddress, sender));
            UdpEvent event = channel.readInbound();
            assertNotNull(event);
            assertEquals(sender, event.getAddress(), "the address is where the datagram came from");
        }
    }

    @Test
    void testUnsignedPacketIsDropped() {
        KadPingMessage ping = new KadPingMessage(fromNode, toNode);
        Bytes wire = Bytes.concatenate(Bytes.of(ping.getCode().toByte()), Bytes.wrap(ping.getBody()));
        assertFalse(channel.writeInbound(packetOf(wire)));
        assertNull(channel.readInbound());
    }

    @Test
    void testTamperedPacketIsDropped() {
        byte[] wire = KadPacket.encode(new KadPingMessage(fromNode, toNode), config.getNetworkId(), senderKey).toArray();
        wire[wire.length - 1] ^= 0x01; // one bit of the body
        assertFalse(channel.writeInbound(packetOf(Bytes.wrap(wire))));
        assertNull(channel.readInbound());
        byte[] wire2 = KadPacket.encode(new KadPingMessage(fromNode, toNode), config.getNetworkId(), senderKey).toArray();
        wire2[10] ^= 0x01; // one bit of the signature
        assertFalse(channel.writeInbound(packetOf(Bytes.wrap(wire2))));
    }

    @Test
    void testPacketOfAnotherNetworkIsDropped() {
        Bytes wire = KadPacket.encode(new KadPingMessage(fromNode, toNode), (byte) 7, senderKey);
        assertFalse(channel.writeInbound(packetOf(wire)));
        assertNull(channel.readInbound());
    }

    @Test
    void testNonDiscoveryMessageIsDropped() {
        // a TCP-level message signed like a discovery packet is still not a discovery packet
        PingMessage ping = new PingMessage();
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> KadPacket.encode(ping, config.getNetworkId(), senderKey)).getMessage().contains("not a discovery"));
        Bytes wire = Bytes.concatenate(Bytes.of(ping.getCode().toByte()), Bytes.random(65), Bytes.wrap(ping.getBody()));
        assertFalse(channel.writeInbound(packetOf(wire)));
    }

    @Test
    void testDecodeEmptyPacket() {
        assertFalse(channel.writeInbound(packetOf(Bytes.EMPTY)));
        assertNull(channel.readInbound());
    }

    @Test
    void testDecodeSingleBytePacket() {
        assertFalse(channel.writeInbound(packetOf(Bytes.of(0))));
    }

    @Test
    void testDecodeHeaderOnlyPacket() {
        assertFalse(channel.writeInbound(packetOf(Bytes.random(KadPacket.HEADER_LENGTH))));
    }

    @Test
    void testDecodeOversizedPacket() {
        assertFalse(channel.writeInbound(packetOf(Bytes.random(P2pPacketDecoder.MAXSIZE + 1))));
        assertNull(channel.readInbound());
    }

    @Test
    void testDecodeCorruptedMessageBody() {
        // a valid signature over a body that does not decode: dropped without an exception reaching the socket
        Bytes wire = Bytes.concatenate(Bytes.of(MessageCode.KAD_PING.toByte()), Bytes.random(65), Bytes.random(20));
        assertFalse(channel.writeInbound(packetOf(wire)));
        assertTrue(channel.isOpen());
    }

    @Test
    void testDecodeNearMaxSizePacket() {
        // the largest neighbours message that fits is still decoded
        java.util.ArrayList<Node> many = new java.util.ArrayList<>();
        for (int i = 0; i < io.xdag.p2p.discover.kad.NodeHandler.NEIGHBORS_PER_PACKET; i++) {
            // IPv6 hosts: the longest a node description gets
            many.add(new Node(ECKeyPair.generate().toAddress().toHexString(), null, "2001:db8:ffff:ffff:ffff:ffff:ffff:" + Integer.toHexString(i + 1), 30303));
        }
        Bytes wire = KadPacket.encode(new KadNeighborsMessage(fromNode, many), config.getNetworkId(), senderKey);
        assertTrue(wire.size() <= KadPacket.MAX_LENGTH);
        ByteBuf buf = Unpooled.wrappedBuffer(wire.toArray());
        channel.writeInbound(new DatagramPacket(buf, localAddress, senderAddress));
        UdpEvent event = channel.readInbound();
        assertNotNull(event);
        assertEquals(io.xdag.p2p.discover.kad.NodeHandler.NEIGHBORS_PER_PACKET, ((KadNeighborsMessage) event.getMessage()).getNeighbors().size());
    }

    @Test
    void testTooManyNeighboursOnTheWireAreRefused() {
        // 17 nodes cannot be built with the constructor; write the count by hand
        io.xdag.p2p.utils.SimpleEncoder enc = new io.xdag.p2p.utils.SimpleEncoder();
        enc.writeBytes(fromNode.toBytes());
        enc.writeInt(KadNeighborsMessage.MAX_NEIGHBORS + 1);
        for (int i = 0; i < KadNeighborsMessage.MAX_NEIGHBORS + 1; i++) {
            enc.writeBytes(toNode.toBytes());
        }
        enc.writeLong(System.currentTimeMillis());
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> new KadNeighborsMessage(enc.toBytes()));
    }
}
