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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XdagFrameCodecTest {

    private P2pConfig config;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        config = new P2pConfig();
        config.setNetMaxFrameBodySize(1024); // 1KB for testing
        XdagFrameCodec codec = new XdagFrameCodec(config);
        channel = new EmbeddedChannel(codec);
    }

    @Test
    void testEncodeDecodeSimpleFrame() {
        byte[] body = "Hello, XDAG!".getBytes();
        XdagFrame originalFrame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        // Test encoding
        assertTrue(channel.writeOutbound(originalFrame));
        assertTrue(channel.finish());

        ByteBuf encoded = channel.readOutbound();
        assertEquals(XdagFrame.HEADER_SIZE + body.length, encoded.readableBytes());

        // Test decoding
        assertTrue(channel.writeInbound(encoded));
        assertTrue(channel.finish());

        XdagFrame decodedFrame = channel.readInbound();
        assertNotNull(decodedFrame);
        assertEquals(originalFrame.getPacketId(), decodedFrame.getPacketId());
        assertArrayEquals(originalFrame.getBody(), decodedFrame.getBody());
    }

    @Test
    void testDecodeIncompleteHeader() {
        ByteBuf incompleteHeader = Unpooled.buffer(XdagFrame.HEADER_SIZE - 1);
        incompleteHeader.writeBytes(new byte[XdagFrame.HEADER_SIZE - 1]);

        assertFalse(channel.writeInbound(incompleteHeader));
        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    void testDecodeIncompleteBody() {
        byte[] body = "test".getBytes();
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        ByteBuf buf = Unpooled.buffer();
        frame.writeHeader(buf);
        // Write only part of the body
        buf.writeBytes(body, 0, body.length - 1);

        assertFalse(channel.writeInbound(buf));
        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    void testDecodeInvalidVersion() {
        XdagFrame frame = new XdagFrame((short) (XdagFrame.VERSION + 1), XdagFrame.COMPRESS_NONE, (byte) 1, 1, 4, 4, new byte[4]);
        ByteBuf buf = Unpooled.buffer();
        frame.writeHeader(buf);
        buf.writeBytes(frame.getBody());

        Exception e = assertThrows(Exception.class, () -> channel.writeInbound(buf));
        assertTrue(e.getCause() instanceof IOException);
        assertTrue(e.getCause().getMessage().contains("Invalid frame version"));
    }

    @Test
    void testDecodeFrameTooLarge() {
        byte[] body = new byte[config.getNetMaxFrameBodySize() + 1];
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        ByteBuf buf = Unpooled.buffer();
        frame.writeHeader(buf); // Header contains the large size

        Exception e = assertThrows(Exception.class, () -> channel.writeInbound(buf));
        assertTrue(e.getCause() instanceof IOException);
        assertTrue(e.getCause().getMessage().contains("Frame body too large"));
    }

    @Test
    void testEncodeFrameBodyTooLarge() {
        byte[] body = new byte[config.getNetMaxFrameBodySize() + 1];
        XdagFrame largeFrame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        // Encoding a too-large frame should not write anything to the outbound buffer
        // and should log an error (which we can't easily test here).
        assertFalse(channel.writeOutbound(largeFrame));
        assertFalse(channel.finish());
        assertNull(channel.readOutbound());
    }
}
