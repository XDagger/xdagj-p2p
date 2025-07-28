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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeTableTest {
  private final P2pConfig p2pConfig = new P2pConfig();
  private Node homeNode;
  private NodeTable nodeTable;
  private String[] ips;
  private List<Bytes> ids;

  @Test
  public void test() {
    Node node1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10002));

    NodeTable table = new NodeTable(node1);
    Node nodeTemp = table.getNode();
    assertEquals(10002, nodeTemp.getPort());
    assertEquals(0, table.getNodesCount());
    assertEquals(0, table.getBucketsCount());

    Node node2 = new Node(p2pConfig, new InetSocketAddress("127.0.0.2", 10003));
    Node node3 = new Node(p2pConfig, new InetSocketAddress("127.0.0.3", 10004));
    table.addNode(node2);
    table.addNode(node3);
    int bucketsCount = table.getBucketsCount();
    int nodeCount = table.getNodesCount();
    assertEquals(2, nodeCount);
    assertTrue(bucketsCount > 0);

    boolean isExist = table.contains(node2);
    table.touchNode(node2);
    assertTrue(isExist);

    Bytes targetId = NetUtils.getNodeId();
    List<Node> nodeList = table.getClosestNodes(targetId);
    assertFalse(nodeList.isEmpty());
  }

  /** init nodes for test. */
  @BeforeEach
  public void init() {
    ids = new ArrayList<>();
    for (int i = 0; i < KademliaOptions.BUCKET_SIZE + 1; i++) {
      byte[] id = new byte[64];
      id[0] = 17;
      id[1] = 16;
      if (i < 10) {
        id[63] = (byte) i;
      } else {
        id[62] = 1;
        id[63] = (byte) (i - 10);
      }
      ids.add(Bytes.wrap(id));
    }

    ips = new String[KademliaOptions.BUCKET_SIZE + 1];
    Bytes homeId = Bytes.wrap(new byte[64]);
    homeNode = new Node(p2pConfig, homeId, "127.0.0.1", null, 16783, 16783);
    nodeTable = new NodeTable(homeNode);
    ips[0] = "127.0.0.2";
    ips[1] = "127.0.0.3";
    ips[2] = "127.0.0.4";
    ips[3] = "127.0.0.5";
    ips[4] = "127.0.0.6";
    ips[5] = "127.0.0.7";
    ips[6] = "127.0.0.8";
    ips[7] = "127.0.0.9";
    ips[8] = "127.0.0.10";
    ips[9] = "127.0.0.11";
    ips[10] = "127.0.0.12";
    ips[11] = "127.0.0.13";
    ips[12] = "127.0.0.14";
    ips[13] = "127.0.0.15";
    ips[14] = "127.0.0.16";
    ips[15] = "127.0.0.17";
    ips[16] = "127.0.0.18";
  }

  @Test
  public void addNodeTest() {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    assertEquals(0, nodeTable.getNodesCount());
    nodeTable.addNode(node);
    assertEquals(1, nodeTable.getNodesCount());
    assertTrue(nodeTable.contains(node));
  }

  @Test
  public void addDupNodeTest() throws Exception {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    nodeTable.addNode(node);
    long firstTouchTime = nodeTable.getAllNodes().getFirst().getModified();
    TimeUnit.MILLISECONDS.sleep(20);
    nodeTable.addNode(node);
    long lastTouchTime = nodeTable.getAllNodes().getFirst().getModified();
    assertTrue(lastTouchTime > firstTouchTime);
    assertEquals(1, nodeTable.getNodesCount());
  }

  @Test
  public void testAddNodeBucketFull() throws Exception {
    for (int i = 0; i < KademliaOptions.BUCKET_SIZE; i++) {
      TimeUnit.MILLISECONDS.sleep(10);
      addNode(new Node(p2pConfig, ids.get(i), ips[i], null, 16783, 16783));
    }
    Node lastSeen =
        nodeTable.addNode(new Node(p2pConfig, ids.get(16), ips[16], null, 16783, 16783));
    assertNotNull(lastSeen);
    assertEquals(ips[15], lastSeen.getHostV4());
  }

  public void addNode(Node n) {
    nodeTable.addNode(n);
  }

  @Test
  public void dropNodeTest() {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    nodeTable.addNode(node);
    assertTrue(nodeTable.contains(node));
    nodeTable.dropNode(node);
    assertFalse(nodeTable.contains(node));
    nodeTable.addNode(node);
    nodeTable.dropNode(new Node(p2pConfig, ids.get(1), ips[0], null, 10000, 10000));
    assertFalse(nodeTable.contains(node));
  }

  @Test
  public void getBucketsCountTest() {
    assertEquals(0, nodeTable.getBucketsCount());
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    nodeTable.addNode(node);
    assertEquals(1, nodeTable.getBucketsCount());
  }

  @Test
  public void touchNodeTest() throws Exception {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    nodeTable.addNode(node);
    long firstTouchTime = nodeTable.getAllNodes().getFirst().getModified();
    TimeUnit.MILLISECONDS.sleep(10);
    nodeTable.touchNode(node);
    long lastTouchTime = nodeTable.getAllNodes().getFirst().getModified();
    assertTrue(firstTouchTime < lastTouchTime);
  }

  @Test
  public void containsTest() {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783);
    assertFalse(nodeTable.contains(node));
    nodeTable.addNode(node);
    assertTrue(nodeTable.contains(node));
  }

  @Test
  public void getBuckIdTest() {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783, 16783); // id: 11100...000
    nodeTable.addNode(node);
    NodeEntry nodeEntry = new NodeEntry(homeNode.getId(), node);
    assertEquals(13, nodeTable.getBucketId(nodeEntry));
  }

  @Test
  public void testGetClosestNodesMoreThanBucketCapacity() throws Exception {
    byte[] bytes = new byte[64];
    bytes[0] = 15;
    Node nearNode = new Node(p2pConfig, Bytes.wrap(bytes), "127.0.0.19", null, 16783, 16783);
    bytes[0] = 70;
    Node farNode = new Node(p2pConfig, Bytes.wrap(bytes), "127.0.0.20", null, 16783, 16783);
    nodeTable.addNode(nearNode);
    nodeTable.addNode(farNode);
    for (int i = 0; i < KademliaOptions.BUCKET_SIZE - 1; i++) {
      // To control totally 17 nodes, however, closest's capacity is 16
      nodeTable.addNode(new Node(p2pConfig, ids.get(i), ips[i], null, 16783, 16783));
      TimeUnit.MILLISECONDS.sleep(10);
    }
    assertTrue(nodeTable.getBucketsCount() > 1);
    // 3 buckets, nearnode's distance is 252, far's is 255, others' are 253
    List<Node> closest = nodeTable.getClosestNodes(homeNode.getId());
    assertTrue(closest.contains(nearNode));
    // the farest node should be excluded
  }

  @Test
  public void testGetClosestNodesIsDiscoverNode() {
    Node node = new Node(p2pConfig, ids.getFirst(), ips[0], null, 16783);
    nodeTable.addNode(node);
    List<Node> closest = nodeTable.getClosestNodes(homeNode.getId());
    assertFalse(closest.isEmpty());
  }
}
