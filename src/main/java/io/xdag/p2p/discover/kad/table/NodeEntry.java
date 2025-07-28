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

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.BytesUtils;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

@Getter
public class NodeEntry {
  private final Node node;
  private final String entryId;
  private final int distance;
  private long modified;

  public NodeEntry(Bytes ownerId, Node n) {
    this.node = n;
    entryId = n.getHostKey();
    distance = distance(ownerId, n.getId());
    touch();
  }

  /**
   * Calculate distance between two node IDs using Tuweni Bytes for better performance. Distance is
   * calculated as the position of the first differing bit.
   *
   * @param ownerId our node ID
   * @param targetId target node ID
   * @return distance value (higher means more distant)
   */
  public static int distance(Bytes ownerId, Bytes targetId) {
    if (ownerId == null || targetId == null) {
      return KademliaOptions.BINS;
    }

    // Handle different lengths by taking the minimum
    int minLength = Math.min(ownerId.size(), targetId.size());
    if (minLength == 0) {
      return KademliaOptions.BINS;
    }

    // Truncate to the same length for XOR
    Bytes ownerTrunc = BytesUtils.take(ownerId, minLength);
    Bytes targetTrunc = BytesUtils.take(targetId, minLength);

    // XOR operation using Tuweni Bytes
    Bytes xorResult = BytesUtils.xor(ownerTrunc, targetTrunc);

    // Count leading zero bits efficiently using ByteUtils
    int leadingZeroBits = BytesUtils.leadingZeroBits(xorResult);

    // Distance calculation: BINS - leading zero bits
    return KademliaOptions.BINS - leadingZeroBits;
  }

  public void touch() {
    modified = System.currentTimeMillis();
  }

  public String getId() {
    return entryId;
  }

  @Override
  public boolean equals(Object o) {
    boolean ret = false;

    if (o != null && this.getClass() == o.getClass()) {
      NodeEntry e = (NodeEntry) o;
      ret = this.getId().equals(e.getId());
    }

    return ret;
  }

  @Override
  public int hashCode() {
    return this.entryId.hashCode();
  }
}
