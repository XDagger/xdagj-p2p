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
package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.discover.Node;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class DistanceComparatorTest {

    @Test
    public void testCompare() {
        byte[] targetBytes = new byte[64];
        Bytes targetId = Bytes.wrap(targetBytes);

        byte[] id1Bytes = new byte[64];
        id1Bytes[63] = 0x01;
        Node node1 = new Node(bytesToHex(id1Bytes), "127.0.0.1", null, 30301, 30301);

        byte[] id2Bytes = new byte[64];
        id2Bytes[0] = (byte) 0x80;
        Node node2 = new Node(bytesToHex(id2Bytes), "127.0.0.1", null, 30302, 30302);

        byte[] id3Bytes = new byte[64];
        id3Bytes[63] = 0x02;
        Node node3 = new Node(bytesToHex(id3Bytes), "127.0.0.1", null, 30303, 30303);

        List<Node> nodes = new ArrayList<>();
        nodes.add(node2);
        nodes.add(node3);
        nodes.add(node1);

        nodes.sort(new DistanceComparator(targetId));

        assertEquals(node1, nodes.get(0));
        assertEquals(node3, nodes.get(1));
        assertEquals(node2, nodes.get(2));
    }

    private String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
