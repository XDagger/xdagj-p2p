package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class NodeEntryTest {

  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void test() throws InterruptedException {
    Node node1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10001));
    NodeEntry nodeEntry = new NodeEntry(Bytes.wrap(NetUtils.getNodeId()), node1);

    long lastModified = nodeEntry.getModified();
    Thread.sleep(1);
    nodeEntry.touch();
    long nowModified = nodeEntry.getModified();
    assertNotEquals(lastModified, nowModified);

    Node node2 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10002));
    NodeEntry nodeEntry2 = new NodeEntry(Bytes.wrap(NetUtils.getNodeId()), node2);
    boolean isDif = nodeEntry.equals(nodeEntry2);
    assertTrue(isDif);
  }

  @Test
  public void testDistance() {
    Bytes randomId = NetUtils.getNodeId();
    String hexRandomIdStr = BytesUtils.toHexString(randomId);
    assertEquals(128, hexRandomIdStr.length()); // 64 bytes = 128 hex characters

    // All node IDs as 64 bytes (128 hex characters)
    Bytes nodeId1 =
        BytesUtils.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId2 =
        BytesUtils.fromHexString(
            "a000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    assertEquals(17, NodeEntry.distance(nodeId1, nodeId2));

    Bytes nodeId3 =
        BytesUtils.fromHexString(
            "0000800000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    assertEquals(1, NodeEntry.distance(nodeId1, nodeId3));

    Bytes nodeId4 =
        BytesUtils.fromHexString(
            "0000400000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    assertEquals(0, NodeEntry.distance(nodeId1, nodeId4));

    Bytes nodeId5 =
        BytesUtils.fromHexString(
            "0000200000000000000000000000000000000000000000000000000000000000"
                + "4000000000000000000000000000000000000000000000000000000000000000");
    assertEquals(-1, NodeEntry.distance(nodeId1, nodeId5));

    Bytes nodeId6 =
        BytesUtils.fromHexString(
            "0000100000000000000000000000000000000000000000000000000000000000"
                + "2000000000000000000000000000000000000000000000000000000000000000");
    assertEquals(-2, NodeEntry.distance(nodeId1, nodeId6));

    Bytes nodeId7 =
        BytesUtils.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000001");
    assertEquals(
        -494, NodeEntry.distance(nodeId1, nodeId7)); // Last bit set for 512-bit ID with BINS=17
  }
}
