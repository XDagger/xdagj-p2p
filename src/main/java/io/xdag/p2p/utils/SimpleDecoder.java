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

import io.xdag.p2p.SimpleCodecException;
import java.io.UnsupportedEncodingException;

/**
 * A simple decoder for reading primitive types and byte arrays from a byte array
 */
public class SimpleDecoder {

    private static final String ENCODING = "UTF-8";

    private final byte[] in;
    private final int from;
    private final int to;

    private int index;

    /**
     * Creates a decoder for the entire byte array
     * @param in byte array to decode
     */
    public SimpleDecoder(byte[] in) {
        this(in, 0, in.length);
    }

    /**
     * Creates a decoder starting from specified position
     * @param in byte array to decode
     * @param from starting position
     */
    public SimpleDecoder(byte[] in, int from) {
        this(in, from, in.length);
    }

    /**
     * Creates a decoder for a range in the byte array
     * @param in byte array to decode
     * @param from starting position
     * @param to ending position
     */
    public SimpleDecoder(byte[] in, int from, int to) {
        this.in = in;
        this.from = from;
        this.to = to;
        this.index = from;
    }

    /**
     * Reads a boolean value
     * @return decoded boolean value
     */
    public boolean readBoolean() {
        require(1);
        return in[index++] != 0;
    }

    /**
     * Reads a single byte
     * @return decoded byte value
     */
    public byte readByte() {
        require(1);
        return in[index++];
    }

    /**
     * Reads a short value (2 bytes)
     * @return decoded short value
     */
    public short readShort() {
        require(2);
        return (short) ((in[index++] & 0xFF) << 8 | (in[index++] & 0xFF));
    }

    /**
     * Reads an integer value (4 bytes)
     * @return decoded integer value
     */
    public int readInt() {
        require(4);
        return in[index++] << 24 | (in[index++] & 0xFF) << 16 | (in[index++] & 0xFF) << 8 | (in[index++] & 0xFF);
    }

    /**
     * Reads a long value (8 bytes)
     * @return decoded long value
     */
    public long readLong() {
        int i1 = readInt();
        int i2 = readInt();

        return (unsignedInt(i1) << 32) | unsignedInt(i2);
    }

    /**
     * Reads a byte array with length prefix
     * @param vlq if true, use variable length quantity encoding for length
     * @return decoded byte array
     */
    public byte[] readBytes(boolean vlq) {
        // null marker
        if (readBoolean()) {
            return null;
        }
        int len = vlq ? readSize() : readInt();
        if (len < 0) {
            // a 4-byte length with the sign bit set; require() would let it through and the allocation below
            // would throw NegativeArraySizeException instead of the documented IndexOutOfBoundsException
            throw new IndexOutOfBoundsException("negative length: " + len);
        }

        require(len);
        byte[] buf = new byte[len];
        System.arraycopy(in, index, buf, 0, len);
        index += len;

        return buf;
    }

    /**
     * Reads a byte array using variable length quantity encoding for length
     * @return decoded byte array
     */
    public byte[] readBytes() {
        return readBytes(true);
    }

    /**
     * Reads a fixed number of bytes into the provided buffer
     * @param buf destination buffer
     * @return number of bytes read
     * @throws IndexOutOfBoundsException if there are not enough bytes
     */
    public int readBytes(byte[] buf) {
        int len = buf.length;
        require(len);
        System.arraycopy(in, index, buf, 0, len);
        index += len;
        return len;
    }

    /**
     * Reads a UTF-8 encoded string
     * @return decoded string
     * @throws SimpleCodecException if encoding is not supported
     */
    public String readString() {
        // null marker for strings
        if (readBoolean()) {
            return null;
        }
        try {
            return new String(readBytes(), ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new SimpleCodecException(e);
        }
    }

    /**
     * Reads a size value using variable length quantity encoding
     * @return decoded size value
     */
    protected int readSize() {
        int size = 0;
        for (int i = 0; i < 4; i++) {
            require(1);
            byte b = in[index++];

            size = (size << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return size;
    }

    /**
     * Checks if there are enough remaining bytes to read
     * @param n number of bytes required
     * @throws IndexOutOfBoundsException if there are not enough bytes
     */
    protected void require(int n) {
        if (n < 0 || to - index < n) {
            String msg = String.format("input [%d, %d], require: [%d %d]", from, to, index, index + n);
            throw new IndexOutOfBoundsException(msg);
        }
    }

    /**
     * Number of bytes that have not been read yet.
     */
    public int remaining() {
        return to - index;
    }

    /**
     * Reads an element count that came from the wire. The count is only a claim of the sender, so it must not be
     * used to allocate anything before it has been checked: it has to be within {@code [0, max]} and the
     * remaining input has to be able to hold that many elements of at least {@code minElementSize} bytes.
     *
     * @throws IndexOutOfBoundsException if the count is negative, above the limit, or larger than the input allows
     */
    public int readCount(int max, int minElementSize) {
        int count = readInt();
        if (count < 0 || count > max || (long) count * Math.max(1, minElementSize) > remaining()) {
            throw new IndexOutOfBoundsException("invalid element count: " + count);
        }
        return count;
    }

    /**
     * Converts a signed integer to an unsigned long value
     * @param i signed integer value
     * @return unsigned long value
     */
    protected long unsignedInt(int i) {
        return i & 0x00000000ffffffffL;
    }
}
