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

public class XdagFrameCodecTest {

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

        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded);
        assertEquals(XdagFrame.HEADER_SIZE + body.length, encoded.readableBytes());

        // Test decoding - need a new channel since we need to decode
        XdagFrameCodec decodeCodec = new XdagFrameCodec(config);
        EmbeddedChannel decodeChannel = new EmbeddedChannel(decodeCodec);

        assertTrue(decodeChannel.writeInbound(encoded));

        XdagFrame decodedFrame = decodeChannel.readInbound();
        assertNotNull(decodedFrame);
        assertEquals(originalFrame.getPacketId(), decodedFrame.getPacketId());
        assertArrayEquals(originalFrame.getBody(), decodedFrame.getBody());

        decodeChannel.finish();
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
        // With the new fault-tolerant design, invalid version frames are skipped, not rejected with exception
        XdagFrame frame = new XdagFrame((short) (XdagFrame.VERSION + 1), XdagFrame.COMPRESS_NONE, (byte) 1, 1, 4, 4, new byte[4]);
        ByteBuf buf = Unpooled.buffer();
        frame.writeHeader(buf);
        buf.writeBytes(frame.getBody());

        // The codec will skip invalid version frames instead of throwing exception
        assertFalse(channel.writeInbound(buf));
        assertNull(channel.readInbound()); // No frame should be decoded
    }

    @Test
    void testDecodeFrameTooLarge() {
        // With the new fault-tolerant design, oversized frames are skipped, not rejected with exception
        byte[] body = new byte[config.getNetMaxFrameBodySize() + 1];
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        ByteBuf buf = Unpooled.buffer();
        frame.writeHeader(buf); // Header contains the large size

        // The codec will skip oversized frames instead of throwing exception
        assertFalse(channel.writeInbound(buf));
        assertNull(channel.readInbound()); // No frame should be decoded
    }

    @Test
    void testEncodeFrameBodyTooLarge() {
        byte[] body = new byte[config.getNetMaxFrameBodySize() + 1];
        XdagFrame largeFrame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);

        // The encoder will log an error and not write anything when the frame is too large
        assertTrue(channel.writeOutbound(largeFrame));

        // The encoder rejects the frame by not writing to the buffer, so we get an empty ByteBuf
        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded);
        assertEquals(0, encoded.readableBytes(), "Encoder should not produce any bytes for frames that are too large");
    }
}
