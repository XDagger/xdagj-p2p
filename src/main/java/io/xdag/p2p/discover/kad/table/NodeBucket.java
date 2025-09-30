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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class NodeBucket {
  private final int depth;
  private final List<NodeEntry> nodes = new ArrayList<>();

  NodeBucket(int depth) {
    this.depth = depth;
  }

  public synchronized NodeEntry addNode(NodeEntry e) {
    if (!nodes.contains(e)) {
      if (nodes.size() >= KademliaOptions.BUCKET_SIZE) {
        return getLastSeen();
      } else {
        nodes.add(e);
      }
    }

    return null;
  }

  private NodeEntry getLastSeen() {
    List<NodeEntry> sorted = new ArrayList<>(nodes);
    sorted.sort(new TimeComparator());
    return sorted.getFirst();
  }

  public synchronized void dropNode(NodeEntry entry) {
    for (NodeEntry e : nodes) {
      if (e.getId().equals(entry.getId())) {
        nodes.remove(e);
        break;
      }
    }
  }

  public int getNodesCount() {
    return nodes.size();
  }
}
