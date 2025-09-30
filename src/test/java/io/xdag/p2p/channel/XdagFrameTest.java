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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XdagXdagFrameTest {

    @Test
    void testXdagFrameCreation() {
        byte[] body = {1, 2, 3, 4};
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 123, body.length, body.length, body);

        assertEquals(XdagFrame.VERSION, frame.getVersion());
        assertEquals(XdagFrame.COMPRESS_NONE, frame.getCompressType());
        assertEquals((byte) 1, frame.getPacketType());
        assertEquals(123, frame.getPacketId());
        assertEquals(body.length, frame.getPacketSize());
        assertEquals(body.length, frame.getBodySize());
        assertArrayEquals(body, frame.getBody());
    }

    @Test
    void testIsChunked() {
        byte[] body = {1, 2, 3, 4};
        // Not chunked
        XdagFrame frame1 = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length, body.length, body);
        assertFalse(frame1.isChunked());

        // Chunked
        XdagFrame frame2 = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 1, body.length * 2, body.length, body);
        assertTrue(frame2.isChunked());
    }

    @Test
    void testWriteHeaderReadHeader() {
        byte[] body = {5, 6, 7, 8};
        XdagFrame originalXdagFrame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_SNAPPY, (byte) 2, 456, 1000, body.length, body);

        ByteBuf buf = Unpooled.buffer(XdagFrame.HEADER_SIZE);
        originalXdagFrame.writeHeader(buf);

        assertEquals(XdagFrame.HEADER_SIZE, buf.writerIndex());

        XdagFrame decodedXdagFrame = XdagFrame.readHeader(buf);

        assertEquals(originalXdagFrame.getVersion(), decodedXdagFrame.getVersion());
        assertEquals(originalXdagFrame.getCompressType(), decodedXdagFrame.getCompressType());
        assertEquals(originalXdagFrame.getPacketType(), decodedXdagFrame.getPacketType());
        assertEquals(originalXdagFrame.getPacketId(), decodedXdagFrame.getPacketId());
        assertEquals(originalXdagFrame.getPacketSize(), decodedXdagFrame.getPacketSize());
        assertEquals(originalXdagFrame.getBodySize(), decodedXdagFrame.getBodySize());
        assertNull(decodedXdagFrame.getBody()); // readHeader only reads the header
    }

    @Test
    void testToString() {
        byte[] body = {1, 2, 3, 4};
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, (byte) 1, 123, body.length, body.length, body);
        String expected = "XdagFrame [version=0, compressType=0, packetType=1, packetId=123, packetSize=4, bodySize=4]";
        assertEquals(expected, frame.toString());
    }
}
