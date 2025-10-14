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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class P2pPacketDecoderTest {

    private P2pConfig config;
    private EmbeddedChannel channel;
    private InetSocketAddress senderAddress;
    private InetSocketAddress localAddress;
    private Node fromNode;
    private Node toNode;

    @BeforeEach
    void setUp() {
        config = new P2pConfig();
        P2pPacketDecoder decoder = new P2pPacketDecoder(config);
        channel = new EmbeddedChannel(decoder);

        senderAddress = new InetSocketAddress("127.0.0.1", 8080);
        localAddress = new InetSocketAddress("0.0.0.0", 30303);

        // Create test nodes with random IDs
        String fromId = Bytes.random(20).toUnprefixedHexString();
        String toId = Bytes.random(20).toUnprefixedHexString();
        fromNode = new Node(fromId, "192.168.1.100", null, 30303);
        toNode = new Node(toId, "192.168.1.101", null, 30303);
    }

    // ==================== Basic Decode Tests ====================

    @Test
    void testDecodeValidKadPingMessage() {
        // Create a valid KAD_PING message
        KadPingMessage ping = new KadPingMessage(fromNode, toNode);

        // Create packet: message code + body
        byte[] data = new byte[ping.getBody().length + 1];
        data[0] = ping.getCode().toByte();
        System.arraycopy(ping.getBody(), 0, data, 1, ping.getBody().length);

        ByteBuf buf = Unpooled.wrappedBuffer(data);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Verify
        UdpEvent event = channel.readInbound();
        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals(MessageCode.KAD_PING, event.getMessage().getCode());
        assertEquals(senderAddress, event.getAddress());
        assertInstanceOf(KadPingMessage.class, event.getMessage());
    }

    @Test
    void testDecodeValidKadPongMessage() {
        // Create a valid KAD_PONG message
        KadPongMessage pong = new KadPongMessage();

        // Create packet: message code + body
        byte[] data = new byte[pong.getBody().length + 1];
        data[0] = pong.getCode().toByte();
        System.arraycopy(pong.getBody(), 0, data, 1, pong.getBody().length);

        ByteBuf buf = Unpooled.wrappedBuffer(data);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Verify
        UdpEvent event = channel.readInbound();
        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals(MessageCode.KAD_PONG, event.getMessage().getCode());
        assertEquals(senderAddress, event.getAddress());
        assertInstanceOf(KadPongMessage.class, event.getMessage());
    }

    @Test
    void testDecodeMultiplePackets() {
        // Decode multiple packets
        for (int i = 0; i < 5; i++) {
            KadPingMessage ping = new KadPingMessage(fromNode, toNode);

            byte[] data = new byte[ping.getBody().length + 1];
            data[0] = ping.getCode().toByte();
            System.arraycopy(ping.getBody(), 0, data, 1, ping.getBody().length);

            ByteBuf buf = Unpooled.wrappedBuffer(data);
            DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

            channel.writeInbound(packet);

            UdpEvent event = channel.readInbound();
            assertNotNull(event);
            assertEquals(MessageCode.KAD_PING, event.getMessage().getCode());
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testDecodeEmptyPacket() {
        // Empty packet (length = 0)
        ByteBuf buf = Unpooled.buffer(0);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should not produce output
        UdpEvent event = channel.readInbound();
        assertNull(event, "Empty packet should be ignored");
    }

    @Test
    void testDecodeSingleBytePacket() {
        // Packet with only 1 byte (length = 1, should be rejected)
        ByteBuf buf = Unpooled.buffer(1);
        buf.writeByte(0x00);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should not produce output
        UdpEvent event = channel.readInbound();
        assertNull(event, "Single byte packet should be ignored");
    }

    @Test
    void testDecodeOversizedPacket() {
        // Packet larger than MAXSIZE (2048 bytes)
        int oversizedLength = P2pPacketDecoder.MAXSIZE + 10;
        ByteBuf buf = Unpooled.buffer(oversizedLength);
        for (int i = 0; i < oversizedLength; i++) {
            buf.writeByte(0);
        }
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should not produce output
        UdpEvent event = channel.readInbound();
        assertNull(event, "Oversized packet should be ignored");
    }

    @Test
    void testDecodeExactlyMaxSizePacket() {
        // Packet exactly at MAXSIZE boundary (should be rejected)
        int maxLength = P2pPacketDecoder.MAXSIZE;
        ByteBuf buf = Unpooled.buffer(maxLength);
        for (int i = 0; i < maxLength; i++) {
            buf.writeByte(0);
        }
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should not produce output
        UdpEvent event = channel.readInbound();
        assertNull(event, "Packet at max size should be ignored");
    }

    @Test
    void testDecodeInvalidMessageCode() {
        // Packet with invalid message code
        ByteBuf buf = Unpooled.buffer(10);
        buf.writeByte((byte) 0xFF); // Invalid message code
        for (int i = 0; i < 9; i++) {
            buf.writeByte(0);
        }
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should produce UdpEvent with null message (MessageFactory returns null for unknown codes)
        UdpEvent event = channel.readInbound();
        assertNotNull(event, "Should produce UdpEvent even for invalid code");
        assertNull(event.getMessage(), "Message should be null for invalid message code");
        assertEquals(senderAddress, event.getAddress());
    }

    @Test
    void testDecodeCorruptedMessageBody() {
        // Packet with valid code but corrupted body
        ByteBuf buf = Unpooled.buffer(10);
        buf.writeByte(MessageCode.KAD_PING.toByte()); // Valid code
        // Add garbage data that can't be decoded as KadPingMessage
        for (int i = 0; i < 9; i++) {
            buf.writeByte((byte) 0xFF);
        }
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should not produce output (exception is caught and logged)
        UdpEvent event = channel.readInbound();
        assertNull(event, "Corrupted message body should be caught");
    }

    // ==================== Edge Cases ====================

    @Test
    void testDecodeMinimalValidPacket() {
        // Minimal valid packet (2 bytes: code + minimal body)
        ByteBuf buf = Unpooled.buffer(2);
        buf.writeByte(MessageCode.KAD_PING.toByte());
        buf.writeByte(0x00); // Minimal body
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode - should fail to parse but not crash
        channel.writeInbound(packet);

        // May or may not produce output depending on parsing
        // The important thing is no exception crashes the decoder
        assertDoesNotThrow(() -> channel.checkException());
    }

    @Test
    void testDecodeNearMaxSizePacket() {
        // Packet just under MAXSIZE (should be accepted if valid)
        KadPingMessage ping = new KadPingMessage(fromNode, toNode);

        byte[] data = new byte[ping.getBody().length + 1];
        data[0] = ping.getCode().toByte();
        System.arraycopy(ping.getBody(), 0, data, 1, ping.getBody().length);

        // Verify it's under MAXSIZE
        assertTrue(data.length < P2pPacketDecoder.MAXSIZE);

        ByteBuf buf = Unpooled.wrappedBuffer(data);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        // Decode
        channel.writeInbound(packet);

        // Should produce output
        UdpEvent event = channel.readInbound();
        assertNotNull(event, "Valid packet under MAXSIZE should be decoded");
    }

    @Test
    void testDecodeDifferentSenderAddresses() {
        // Test packets from different senders
        InetSocketAddress sender1 = new InetSocketAddress("192.168.1.1", 30303);
        InetSocketAddress sender2 = new InetSocketAddress("192.168.1.2", 30304);
        InetSocketAddress sender3 = new InetSocketAddress("10.0.0.1", 40000);

        for (InetSocketAddress sender : new InetSocketAddress[]{sender1, sender2, sender3}) {
            KadPingMessage ping = new KadPingMessage(fromNode, toNode);

            byte[] data = new byte[ping.getBody().length + 1];
            data[0] = ping.getCode().toByte();
            System.arraycopy(ping.getBody(), 0, data, 1, ping.getBody().length);

            ByteBuf buf = Unpooled.wrappedBuffer(data);
            DatagramPacket packet = new DatagramPacket(buf, localAddress, sender);

            channel.writeInbound(packet);

            UdpEvent event = channel.readInbound();
            assertNotNull(event);
            assertEquals(sender, event.getAddress(), "Sender address should match");
        }
    }

    @Test
    void testDecodeAllKadMessageTypes() {
        // Test decoding KAD_PING
        KadPingMessage ping = new KadPingMessage(fromNode, toNode);
        byte[] pingData = new byte[ping.getBody().length + 1];
        pingData[0] = ping.getCode().toByte();
        System.arraycopy(ping.getBody(), 0, pingData, 1, ping.getBody().length);

        ByteBuf buf1 = Unpooled.wrappedBuffer(pingData);
        DatagramPacket packet1 = new DatagramPacket(buf1, localAddress, senderAddress);
        channel.writeInbound(packet1);

        UdpEvent event1 = channel.readInbound();
        assertNotNull(event1, "Should decode KAD_PING");
        assertEquals(MessageCode.KAD_PING, event1.getMessage().getCode());

        // Test decoding KAD_PONG
        KadPongMessage pong = new KadPongMessage();
        byte[] pongData = new byte[pong.getBody().length + 1];
        pongData[0] = pong.getCode().toByte();
        System.arraycopy(pong.getBody(), 0, pongData, 1, pong.getBody().length);

        ByteBuf buf2 = Unpooled.wrappedBuffer(pongData);
        DatagramPacket packet2 = new DatagramPacket(buf2, localAddress, senderAddress);
        channel.writeInbound(packet2);

        UdpEvent event2 = channel.readInbound();
        assertNotNull(event2, "Should decode KAD_PONG");
        assertEquals(MessageCode.KAD_PONG, event2.getMessage().getCode());
    }

    // ==================== Boundary Tests ====================

    @Test
    void testDecodeBoundaryLength2() {
        // Length = 2 (minimum valid length)
        ByteBuf buf = Unpooled.buffer(2);
        buf.writeByte(MessageCode.KAD_PING.toByte());
        buf.writeByte(0x00);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        channel.writeInbound(packet);

        // Should attempt to decode (may fail parsing but shouldn't crash)
        assertDoesNotThrow(() -> channel.checkException());
    }

    @Test
    void testDecodeBoundaryLength2047() {
        // Length = 2047 (MAXSIZE - 1, maximum valid length)
        int validMaxLength = P2pPacketDecoder.MAXSIZE - 1;
        ByteBuf buf = Unpooled.buffer(validMaxLength);
        buf.writeByte(MessageCode.KAD_PING.toByte());
        for (int i = 1; i < validMaxLength; i++) {
            buf.writeByte(0);
        }
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        channel.writeInbound(packet);

        // Should attempt to decode (will fail parsing but shouldn't crash)
        assertDoesNotThrow(() -> channel.checkException());
    }

    @Test
    void testDecodeZeroLengthBuffer() {
        // ByteBuf with 0 readable bytes
        ByteBuf buf = Unpooled.buffer(0);
        DatagramPacket packet = new DatagramPacket(buf, localAddress, senderAddress);

        channel.writeInbound(packet);

        UdpEvent event = channel.readInbound();
        assertNull(event, "Zero-length packet should be ignored");
    }
}
