package io.xdag.p2p.discover.kad.table;

import io.xdag.p2p.discover.Node;
import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes;

public class DistanceComparator implements Comparator<Node> {
  private final Bytes targetId;

  DistanceComparator(Bytes targetId) {
    this.targetId = targetId;
  }

  @Override
  public int compare(Node e1, Node e2) {
    int d1 = NodeEntry.distance(targetId, e1.getId());
    int d2 = NodeEntry.distance(targetId, e2.getId());

    return Integer.compare(d1, d2);
  }
}
