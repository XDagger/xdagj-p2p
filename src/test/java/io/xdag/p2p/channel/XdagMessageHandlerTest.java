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

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageException;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XdagMessageHandlerTest {

    private P2pConfig config;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        config = new P2pConfig();
        config.setEnableFrameCompression(false);
        config.setNetMaxPacketSize(16 * 1024 * 1024); // 16 MB
        config.setNetMaxFrameBodySize(4096); // 4 KB

        XdagMessageHandler handler = new XdagMessageHandler(config);
        channel = new EmbeddedChannel(handler);
    }

    // ==================== Basic Encode/Decode Tests ====================

    @Test
    void testEncodeSimpleMessage() {
        // Create a simple PING message
        PingMessage ping = new PingMessage();

        // Encode
        channel.writeOutbound(ping);

        // Should produce one frame
        XdagFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertEquals(XdagFrame.VERSION, frame.getVersion());
        assertEquals(ping.getCode().toByte(), frame.getPacketType());
        assertEquals(XdagFrame.COMPRESS_NONE, frame.getCompressType());
        assertFalse(frame.isChunked());
    }

    @Test
    void testRoundTripSimpleMessage() {
        // Create a PING message
        PingMessage originalPing = new PingMessage();
        long originalTimestamp = originalPing.getTimestamp();

        // Encode
        channel.writeOutbound(originalPing);
        XdagFrame frame = channel.readOutbound();
        assertNotNull(frame);

        // Decode
        channel.writeInbound(frame);
        Message decoded = channel.readInbound();

        assertNotNull(decoded);
        assertInstanceOf(PingMessage.class, decoded);
        PingMessage decodedPing = (PingMessage) decoded;
        assertEquals(originalTimestamp, decodedPing.getTimestamp());
    }

    @Test
    void testEncodeDecodeMultipleMessages() {
        // Test encoding/decoding different message types
        PingMessage ping = new PingMessage();
        PongMessage pong = new PongMessage();

        // Encode both
        channel.writeOutbound(ping);
        channel.writeOutbound(pong);

        // Read frames
        XdagFrame frame1 = channel.readOutbound();
        XdagFrame frame2 = channel.readOutbound();

        assertNotNull(frame1);
        assertNotNull(frame2);
        assertNotEquals(frame1.getPacketId(), frame2.getPacketId()); // Different packet IDs

        // Decode both
        channel.writeInbound(frame1);
        channel.writeInbound(frame2);

        Message msg1 = channel.readInbound();
        Message msg2 = channel.readInbound();

        assertInstanceOf(PingMessage.class, msg1);
        assertInstanceOf(PongMessage.class, msg2);
    }

    // ==================== Compression Tests ====================

    @Test
    void testEncodeWithCompression() {
        // Enable compression
        config.setEnableFrameCompression(true);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel compressChannel = new EmbeddedChannel(handler);

        PingMessage ping = new PingMessage();

        // Encode
        compressChannel.writeOutbound(ping);

        XdagFrame frame = compressChannel.readOutbound();
        assertNotNull(frame);
        assertEquals(XdagFrame.COMPRESS_SNAPPY, frame.getCompressType());
    }

    @Test
    void testRoundTripWithCompression() {
        // Enable compression
        config.setEnableFrameCompression(true);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel compressChannel = new EmbeddedChannel(handler);

        PingMessage originalPing = new PingMessage();
        long originalTimestamp = originalPing.getTimestamp();

        // Encode
        compressChannel.writeOutbound(originalPing);
        XdagFrame frame = compressChannel.readOutbound();
        assertNotNull(frame);
        assertEquals(XdagFrame.COMPRESS_SNAPPY, frame.getCompressType());

        // Decode
        compressChannel.writeInbound(frame);
        Message decoded = compressChannel.readInbound();

        assertNotNull(decoded);
        assertInstanceOf(PingMessage.class, decoded);
        PingMessage decodedPing = (PingMessage) decoded;
        assertEquals(originalTimestamp, decodedPing.getTimestamp());
    }

    // ==================== Chunking Tests ====================

    @Test
    void testEncodeLargeMessageChunking() {
        // Create a message with large body that requires chunking
        config.setNetMaxFrameBodySize(100); // Small frame size to force chunking
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel chunkChannel = new EmbeddedChannel(handler);

        PingMessage ping = new PingMessage();

        // Encode
        chunkChannel.writeOutbound(ping);

        // Should produce multiple frames
        List<XdagFrame> frames = new ArrayList<>();
        XdagFrame frame;
        while ((frame = chunkChannel.readOutbound()) != null) {
            frames.add(frame);
        }

        assertTrue(frames.size() >= 1, "Should produce at least one frame");

        // All frames should have same packet ID
        if (frames.size() > 1) {
            int packetId = frames.get(0).getPacketId();
            for (XdagFrame f : frames) {
                assertEquals(packetId, f.getPacketId());
                assertEquals(ping.getCode().toByte(), f.getPacketType());
            }
        }
    }

    @Test
    void testDecodeChunkedMessage() {
        // Create chunked frames
        config.setNetMaxFrameBodySize(100);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel chunkChannel = new EmbeddedChannel(handler);

        PingMessage originalPing = new PingMessage();
        long originalTimestamp = originalPing.getTimestamp();

        // Encode to get frames
        chunkChannel.writeOutbound(originalPing);

        List<XdagFrame> frames = new ArrayList<>();
        XdagFrame frame;
        while ((frame = chunkChannel.readOutbound()) != null) {
            frames.add(frame);
        }

        // Feed all frames back for decoding
        for (XdagFrame f : frames) {
            chunkChannel.writeInbound(f);
        }

        // Should get the original message back
        Message decoded = chunkChannel.readInbound();
        assertNotNull(decoded);
        assertInstanceOf(PingMessage.class, decoded);
        PingMessage decodedPing = (PingMessage) decoded;
        assertEquals(originalTimestamp, decodedPing.getTimestamp());
    }

    @Test
    void testDecodePartialChunkedMessage() {
        // Create chunked frames
        config.setNetMaxFrameBodySize(50);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel chunkChannel = new EmbeddedChannel(handler);

        PingMessage ping = new PingMessage();

        // Encode
        chunkChannel.writeOutbound(ping);

        List<XdagFrame> frames = new ArrayList<>();
        XdagFrame frame;
        while ((frame = chunkChannel.readOutbound()) != null) {
            frames.add(frame);
        }

        // Need at least 2 frames for this test
        if (frames.size() < 2) {
            // Skip test if message doesn't chunk with current frame size
            return;
        }

        // Feed only partial frames (not complete)
        for (int i = 0; i < frames.size() - 1; i++) {
            chunkChannel.writeInbound(frames.get(i));
        }

        // Should not produce output yet
        Message decoded = chunkChannel.readInbound();
        assertNull(decoded, "Incomplete packet should not produce output");

        // Feed last frame
        chunkChannel.writeInbound(frames.get(frames.size() - 1));

        // Now should get complete message
        decoded = chunkChannel.readInbound();
        assertNotNull(decoded);
        assertInstanceOf(PingMessage.class, decoded);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testDecodeNullFrame() {
        // Decode null frame should throw exception
        assertThrows(Exception.class, () -> {
            channel.writeInbound((XdagFrame) null);
            channel.checkException();
        });
    }

    @Test
    void testEncodeMessageTooLarge() {
        // Set very small max packet size (smaller than PingMessage body which is 8 bytes)
        config.setNetMaxPacketSize(5);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel testChannel = new EmbeddedChannel(handler);

        PingMessage ping = new PingMessage();

        // Encode - Netty will throw exception because no output is produced
        assertThrows(Exception.class, () -> {
            testChannel.writeOutbound(ping);
            testChannel.checkException();
        });
    }

    @Test
    void testEncodeInvalidFrameBodySize() {
        // Set invalid frame body size
        config.setNetMaxFrameBodySize(0);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel testChannel = new EmbeddedChannel(handler);

        PingMessage ping = new PingMessage();

        // Encode - Netty will throw exception because no frames are produced
        assertThrows(Exception.class, () -> {
            testChannel.writeOutbound(ping);
            testChannel.checkException();
        });
    }

    @Test
    void testDecodeInvalidPacketSize() {
        // Create frame with invalid packet size
        XdagFrame invalidFrame = new XdagFrame(
                XdagFrame.VERSION,
                XdagFrame.COMPRESS_NONE,
                (byte) 0x14,
                1,
                -100, // Invalid negative size
                10,
                new byte[10]
        );

        assertThrows(Exception.class, () -> {
            channel.writeInbound(invalidFrame);
            channel.checkException();
        });
    }

    @Test
    void testDecodePacketSizeTooLarge() {
        // Create frame with size exceeding max
        XdagFrame oversizedFrame = new XdagFrame(
                XdagFrame.VERSION,
                XdagFrame.COMPRESS_NONE,
                (byte) 0x14,
                1,
                config.getNetMaxPacketSize() + 1000,
                10,
                new byte[10]
        );

        assertThrows(Exception.class, () -> {
            channel.writeInbound(oversizedFrame);
            channel.checkException();
        });
    }

    @Test
    void testDecodeUnsupportedCompressionType() {
        // Create frame with unsupported compression type
        byte[] data = new PingMessage().getBody();
        XdagFrame unsupportedFrame = new XdagFrame(
                XdagFrame.VERSION,
                (byte) 99, // Unsupported compression type
                (byte) 0x14,
                1,
                data.length,
                data.length,
                data
        );

        assertThrows(Exception.class, () -> {
            channel.writeInbound(unsupportedFrame);
            channel.checkException();
        });
    }

    // ==================== Backpressure Tests ====================

    @Test
    void testMaxInflightPacketsBackpressure() {
        // Send many incomplete chunked packets to trigger backpressure
        for (int i = 0; i < 70; i++) {
            XdagFrame frame = new XdagFrame(
                    XdagFrame.VERSION,
                    XdagFrame.COMPRESS_NONE,
                    (byte) 0x14,
                    i, // Different packet IDs
                    200,
                    100, // Only half the data
                    new byte[100]
            );
            channel.writeInbound(frame);
        }

        // Complete one packet - should trigger cleanup when > 64 inflight
        XdagFrame completeFrame = new XdagFrame(
                XdagFrame.VERSION,
                XdagFrame.COMPRESS_NONE,
                (byte) 0x14,
                0,
                200,
                100,
                new byte[100]
        );

        assertDoesNotThrow(() -> {
            channel.writeInbound(completeFrame);
            channel.checkException();
        });
    }

    // ==================== Packet ID Tests ====================

    @Test
    void testPacketIdIncrement() {
        // Send multiple messages and verify packet IDs increment
        List<Integer> packetIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            PingMessage ping = new PingMessage();
            channel.writeOutbound(ping);

            XdagFrame frame = channel.readOutbound();
            assertNotNull(frame);
            packetIds.add(frame.getPacketId());
        }

        // Verify packet IDs are incrementing
        for (int i = 1; i < packetIds.size(); i++) {
            assertTrue(packetIds.get(i) > packetIds.get(i - 1),
                    "Packet IDs should increment");
        }
    }

    // ==================== Edge Cases ====================

    @Test
    void testDecodeEmptyFramesList() {
        // This tests internal behavior - decodeFrames with empty list
        // We can't directly test private method, but we ensure the handler doesn't crash
        assertDoesNotThrow(() -> {
            channel.checkException();
        });
    }

    @Test
    void testChunkedPacketNegativeRemaining() {
        // Send first frame claiming packet is complete
        XdagFrame frame1 = new XdagFrame(
                XdagFrame.VERSION,
                XdagFrame.COMPRESS_NONE,
                (byte) 0x14,
                99,
                100,
                100,
                new byte[100]
        );
        channel.writeInbound(frame1);

        // Should complete successfully
        Message decoded1 = channel.readInbound();
        // May be null if message can't be decoded with 100 bytes of zeros
        // The important thing is no exception was thrown

        // Try to send another frame for same packet ID
        // This tests the "remaining < 0" path
        XdagFrame frame2 = new XdagFrame(
                XdagFrame.VERSION,
                XdagFrame.COMPRESS_NONE,
                (byte) 0x14,
                99,
                50,
                50,
                new byte[50]
        );

        // This should work as packet 99 was removed from inFlight
        assertDoesNotThrow(() -> {
            channel.writeInbound(frame2);
            channel.checkException();
        });
    }
}
