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
package io.xdag.p2p.message.discover;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

@Getter
public class KadFindNodeMessage extends Message {

    /** Length of a target: a node id is the 20 bytes of an address. */
    public static final int TARGET_LENGTH = 20;

    private final Node from;
    private final Bytes target;
    private final long timestamp;

    public KadFindNodeMessage(Node from, Bytes target) {
        super(MessageCode.KAD_FIND_NODE, KadNeighborsMessage.class);
        if (target == null || target.size() != TARGET_LENGTH) {
            throw new IllegalArgumentException("target must be " + TARGET_LENGTH + " bytes");
        }
        this.from = from;
        this.target = target;
        this.timestamp = System.currentTimeMillis();

        SimpleEncoder enc = new SimpleEncoder();
        encode(enc);
        this.body = enc.toBytes();
    }

    public KadFindNodeMessage(byte[] body) {
        super(MessageCode.KAD_FIND_NODE, KadNeighborsMessage.class);
        SimpleDecoder dec = new SimpleDecoder(body);
        byte[] fromBytes = dec.readBytes();
        if (fromBytes == null || fromBytes.length == 0) {
            throw new IllegalArgumentException("Invalid KadFindNodeMessage: 'from' node data is missing");
        }
        this.from = new Node(fromBytes);
        byte[] targetBytes = dec.readBytes();
        if (targetBytes == null || targetBytes.length != TARGET_LENGTH) {
            throw new IllegalArgumentException("Invalid KadFindNodeMessage: target must be " + TARGET_LENGTH + " bytes");
        }
        this.target = Bytes.wrap(targetBytes);
        this.timestamp = dec.readLong();
        this.body = body;
    }

    @Override
    public void encode(SimpleEncoder enc) {
        enc.writeBytes(from.toBytes());
        enc.writeBytes(target.toArray());
        enc.writeLong(timestamp);
    }
}
