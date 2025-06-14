package io.xdag.p2p.discover.kad.table;

import java.util.Comparator;

public class TimeComparator implements Comparator<NodeEntry> {

  @Override
  public int compare(NodeEntry e1, NodeEntry e2) {
    long t1 = e1.getModified();
    long t2 = e2.getModified();

    return Long.compare(t2, t1);
  }
}
