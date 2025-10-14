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

/**
 * Represents a network packet frame in the XDAG network protocol. Numbers are signed and in big-endian format.
 * <p>
 * Frame structure:
 * <ul>
 * <li><code>FRAME := HEADER (20 bytes) + BODY (variable length)</code></li>
 * <li><code>HEADER := MAGIC + VERSION + COMPRESS_TYPE + PACKET_TYPE + PACKET_ID + PACKET_SIZE + BODY_SIZE</code></li>
 * <li><code>BODY := BINARY_DATA</code></li>
 * </ul>
 */
public class XdagFrame {

    /**
     * Magic number "XDAG" in ASCII (0x58444147) for frame boundary identification.
     * This is used to detect frame boundaries and resync TCP streams.
     */
    public static final int MAGIC_NUMBER = 0x58444147;  // "XDAG" in ASCII

    /**
     * Header size: 20 bytes
     * = 4 (magic) + 2 (version) + 1 (compress) + 1 (packet type) + 4 (packet id) + 4 (packet size) + 4 (body size)
     */
    public static final int HEADER_SIZE = 20;

    /**
     * Current protocol version
     */
    public static final short VERSION = 1;

    public static final byte COMPRESS_NONE = 0;
    public static final byte COMPRESS_SNAPPY = 1;

    protected final short version;     /* Protocol version, 2 bytes */
    protected final byte compressType; /* Compression type, 1 byte */
    protected final byte packetType;   /* Type of packet, 1 byte */
    protected final int packetId;      /* Unique packet ID, 4 bytes */
    protected final int packetSize;    /* Total packet size, 4 bytes */
    protected final int bodySize;      /* Size of body data, 4 bytes */

    protected byte[] body;

    /**
     * Creates a new Frame with the specified parameters
     *
     * @param version Protocol version
     * @param compressType Type of compression used
     * @param packetType Type of packet
     * @param packetId Unique packet identifier
     * @param packetSize Total size of packet
     * @param bodySize Size of body data
     * @param body Actual body data bytes
     */
    public XdagFrame(short version, byte compressType, byte packetType, int packetId, int packetSize, int bodySize,
            byte[] body) {
        this.version = version;
        this.compressType = compressType;
        this.packetType = packetType;
        this.packetId = packetId;
        this.packetSize = packetSize;
        this.bodySize = bodySize;
        this.body = body;
    }

    public short getVersion() {
        return version;
    }

    public byte getCompressType() {
        return compressType;
    }

    public byte getPacketType() {
        return packetType;
    }

    public int getPacketId() {
        return packetId;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getBodySize() {
        return bodySize;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    /**
     * Checks if the packet is chunked by comparing body size with packet size
     *
     * @return true if packet is chunked, false otherwise
     */
    public boolean isChunked() {
        return bodySize != packetSize;
    }

    /**
     * Writes the frame header fields to the provided ByteBuf
     *
     * @param buf ByteBuf to write header data to
     */
    public void writeHeader(ByteBuf buf) {
        buf.writeInt(MAGIC_NUMBER);  // Magic number for frame sync
        buf.writeShort(getVersion());
        buf.writeByte(getCompressType());
        buf.writeByte(getPacketType());
        buf.writeInt(getPacketId());
        buf.writeInt(getPacketSize());
        buf.writeInt(getBodySize());
    }

    /**
     * Reads frame header data from the provided ByteBuf and creates a new Frame
     *
     * @param in ByteBuf containing header data to read
     * @return new Frame instance with header data (body is null)
     * @throws IllegalArgumentException if magic number is invalid
     */
    public static XdagFrame readHeader(ByteBuf in) {
        int magic = in.readInt();

        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException(
                String.format("Invalid magic number: expected 0x%08X, got 0x%08X", MAGIC_NUMBER, magic));
        }

        short version = in.readShort();
        byte compressType = in.readByte();
        byte packetType = in.readByte();
        int packetId = in.readInt();
        int packetSize = in.readInt();
        int bodySize = in.readInt();

        return new XdagFrame(version, compressType, packetType, packetId, packetSize, bodySize, null);
    }

    @Override
    public String toString() {
        return "XdagFrame [version=" + version + ", compressType=" + compressType + ", packetType=" + packetType
                + ", packetId=" + packetId + ", packetSize=" + packetSize + ", bodySize=" + bodySize + "]";
    }

}
