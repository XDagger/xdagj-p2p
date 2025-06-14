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
    List<NodeEntry> sorted = nodes;
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
