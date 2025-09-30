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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class KadNeighborsMessage extends Message {

    private final Node from;
    private final List<Node> neighbors;
    private final long timestamp;

    public KadNeighborsMessage(Node from, List<Node> neighbors) {
        super(MessageCode.KAD_NEIGHBORS, null);
        this.from = from;
        this.neighbors = neighbors;
        this.timestamp = System.currentTimeMillis();

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(from.toBytes());
        enc.writeInt(neighbors.size());
        for (Node n : neighbors) {
            enc.writeBytes(n.toBytes());
        }
        enc.writeLong(timestamp);
        this.body = enc.toBytes();
    }

    public KadNeighborsMessage(byte[] body) {
        super(MessageCode.KAD_NEIGHBORS, null);
        this.body = body;
        SimpleDecoder dec = new SimpleDecoder(body);
        this.from = new Node(dec.readBytes());
        int size = dec.readInt();
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
