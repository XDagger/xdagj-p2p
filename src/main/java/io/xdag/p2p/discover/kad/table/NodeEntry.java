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

    // Truncate to same length for XOR
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
