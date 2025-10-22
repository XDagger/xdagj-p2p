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
package io.xdag.p2p.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

class SimpleEncoderDecoderTest {

    @Test
    void testByte() {
        SimpleEncoder encoder = new SimpleEncoder();
        byte testValue = (byte) 0xAB;
        encoder.writeByte(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals(testValue, decoder.readByte());
    }

    @Test
    void testShort() {
        SimpleEncoder encoder = new SimpleEncoder();
        short testValue = (short) 30000;
        encoder.writeShort(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals(testValue, decoder.readShort());
    }

    @Test
    void testInt() {
        SimpleEncoder encoder = new SimpleEncoder();
        int testValue = 1_000_000_000;
        encoder.writeInt(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals(testValue, decoder.readInt());
    }

    @Test
    void testLong() {
        SimpleEncoder encoder = new SimpleEncoder();
        long testValue = 9_000_000_000_000_000_000L;
        encoder.writeLong(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals(testValue, decoder.readLong());
    }

    @Test
    void testString() {
        SimpleEncoder encoder = new SimpleEncoder();
        String testValue = "Hello, XDAGJ-P2P!";
        encoder.writeString(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals(testValue, decoder.readString());
    }

    @Test
    void testBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        byte[] testValue = "This is a byte array".getBytes(StandardCharsets.UTF_8);
        encoder.writeBytes(testValue);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertArrayEquals(testValue, decoder.readBytes());
    }

    @Test
    void testCombined() {
        SimpleEncoder encoder = new SimpleEncoder();
        byte b = (byte) 0xFE;
        short s = (short) 12345;
        int i = 987654321;
        long l = 1234567890123456789L;
        String str = "Combined Test";
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};

        encoder.writeByte(b);
        encoder.writeShort(s);
        encoder.writeInt(i);
        encoder.writeLong(l);
        encoder.writeString(str);
        encoder.writeBytes(bytes);

        byte[] encoded = encoder.toBytes();
        SimpleDecoder decoder = new SimpleDecoder(encoded);

        assertEquals(b, decoder.readByte());
        assertEquals(s, decoder.readShort());
        assertEquals(i, decoder.readInt());
        assertEquals(l, decoder.readLong());
        assertEquals(str, decoder.readString());
        assertArrayEquals(bytes, decoder.readBytes());
    }

    @Test
    void testNullString() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeString(null);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertNull(decoder.readString());
    }
    
    @Test
    void testEmptyString() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeString("");
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertEquals("", decoder.readString());
    }

    @Test
    void testNullBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeBytes(null);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertNull(decoder.readBytes());
    }
    
    @Test
    void testEmptyBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeBytes(new byte[0]);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        assertArrayEquals(new byte[0], decoder.readBytes());
    }
}
