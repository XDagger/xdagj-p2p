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
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class KadNeighborsMessage extends Message {

    /** Most nodes in one message: a bucket's worth. */
    public static final int MAX_NEIGHBORS = KademliaOptions.BUCKET_SIZE;
    /** Smallest encoding of a node (all strings null), with its length prefix: bounds the count before reading. */
    private static final int MIN_NODE_LENGTH = 2 + 1 + 2 + 3 + 4 + 4 + 8;

    private final Node from;
    private final List<Node> neighbors;
    private final long timestamp;

    public KadNeighborsMessage(Node from, List<Node> neighbors) {
        super(MessageCode.KAD_NEIGHBORS, null);
        if (neighbors.size() > MAX_NEIGHBORS) {
            throw new IllegalArgumentException("at most " + MAX_NEIGHBORS + " neighbours per message");
        }
        this.from = from;
        this.neighbors = neighbors;
        this.timestamp = System.currentTimeMillis();

        SimpleEncoder enc = new SimpleEncoder();
        encode(enc);
        this.body = enc.toBytes();
    }

    public KadNeighborsMessage(byte[] body) {
        super(MessageCode.KAD_NEIGHBORS, null);
        this.body = body;
        SimpleDecoder dec = new SimpleDecoder(body);
        byte[] fromBytes = dec.readBytes();
        if (fromBytes == null || fromBytes.length == 0) {
            throw new IllegalArgumentException("Invalid KadNeighborsMessage: 'from' node data is missing");
        }
        this.from = new Node(fromBytes);
        // the count is a claim of the sender: bounded before anything is allocated for it
        int size = dec.readCount(MAX_NEIGHBORS, MIN_NODE_LENGTH);
        this.neighbors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.neighbors.add(new Node(dec.readBytes()));
        }
        this.timestamp = dec.readLong();
    }

    @Override
    public void encode(SimpleEncoder enc) {
        enc.writeBytes(from.toBytes());
        enc.writeInt(neighbors.size());
        for (Node n : neighbors) {
            enc.writeBytes(n.toBytes());
        }
        enc.writeLong(timestamp);
    }
}
