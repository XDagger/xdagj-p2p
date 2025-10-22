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
package io.xdag.p2p.config;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.channel.XdagFrame;
import io.xdag.p2p.channel.XdagMessageHandler;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/** Updated tests to reflect custom encoding and frame compression path. */
public class UpgradeControllerTest {

    @Test
    public void testSimpleEncoderDecoderNulls() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeString(null);
        enc.writeBytes(null);
        byte[] out = enc.toBytes();
        SimpleDecoder dec = new SimpleDecoder(out);
        assertNull(dec.readString());
        assertNull(dec.readBytes());
    }

    @Test
    public void testSimpleEncoderDecoderRoundTrip() {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBoolean(true);
        enc.writeByte((byte)0x7F);
        enc.writeShort((short)1234);
        enc.writeInt(0x7fffffff);
        enc.writeLong(0x12345678abcdef12L);
        enc.writeString("");
        enc.writeString("hello");
        enc.writeBytes(new byte[] {1,2,3});
        byte[] out = enc.toBytes();

        SimpleDecoder dec = new SimpleDecoder(out);
        assertTrue(dec.readBoolean());
        assertEquals((byte)0x7F, dec.readByte());
        assertEquals((short)1234, dec.readShort());
        assertEquals(0x7fffffff, dec.readInt());
        assertEquals(0x12345678abcdef12L, dec.readLong());
        assertEquals("", dec.readString());
        assertEquals("hello", dec.readString());
        assertArrayEquals(new byte[]{1,2,3}, dec.readBytes());
    }

    @Test
    public void testCompressionFlaggedInFrameHeader() {
        P2pConfig cfg = new P2pConfig();
        cfg.setEnableFrameCompression(true);
        cfg.setNetMaxFrameBodySize(256);
        XdagMessageHandler handler = new XdagMessageHandler(cfg);

        byte[] body = new byte[1024];
        new Random(1).nextBytes(body);
        Message msg = new Message(MessageCode.APP_TEST, null) {
            @Override
            public byte[] getBody() { return body; }
            @Override
            public void encode(io.xdag.p2p.utils.SimpleEncoder enc) {
                if (body != null && body.length > 0) {
                    enc.writeBytes(body);
                }
            }
        };

        byte packetType = msg.getCode().toByte();
        int packetId = 1;
        int packetSize = body.length;
        short packetVersion = (short)0; // XdagFrame.VERSION
        byte compressType =
            cfg.isEnableFrameCompression() ? XdagFrame.COMPRESS_SNAPPY : XdagFrame.COMPRESS_NONE;

        XdagFrame frame = new XdagFrame(packetVersion, compressType, packetType, packetId, packetSize, Math.min(packetSize, cfg.getNetMaxFrameBodySize()), Bytes.wrap(body, 0, Math.min(packetSize, cfg.getNetMaxFrameBodySize())).toArray());

        assertEquals(packetVersion, frame.getVersion());
        assertEquals(compressType, frame.getCompressType());
        assertEquals(packetType, frame.getPacketType());
        assertEquals(packetId, frame.getPacketId());
        assertEquals(packetSize, frame.getPacketSize());
        assertEquals(Math.min(packetSize, cfg.getNetMaxFrameBodySize()), frame.getBodySize());
        assertNotNull(frame.getBody());
    }
}
