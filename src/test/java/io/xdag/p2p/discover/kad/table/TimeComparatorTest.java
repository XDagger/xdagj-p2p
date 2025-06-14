package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

public class TimeComparatorTest {

  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void test() throws InterruptedException {

    Node node1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10001));
    NodeEntry ne1 = new NodeEntry(NetUtils.getNodeId(), node1);
    Thread.sleep(1);
    Node node2 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10002));
    NodeEntry ne2 = new NodeEntry(NetUtils.getNodeId(), node2);
    TimeComparator tc = new TimeComparator();
    int result = tc.compare(ne1, ne2);
    assertEquals(1, result);
  }
}
