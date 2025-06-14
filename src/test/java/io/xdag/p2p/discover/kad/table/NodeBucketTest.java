package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for NodeBucket - the core Kademlia k-bucket implementation. Tests bucket capacity, node
 * addition, and LRU behavior.
 */
public class NodeBucketTest {

  private NodeBucket bucket;
  private Bytes ownerId;
  private P2pConfig config;
  private int ipCounter = 1;

  @BeforeEach
  public void setUp() {
    bucket = new NodeBucket(0);
    ownerId = Bytes.random(64);
    config = new P2pConfig();
  }

  /**
   * Create a unique NodeEntry with different IP addresses. This ensures nodes are considered
   * different in Kademlia network.
   */
  private NodeEntry createUniqueNodeEntry() {
    String ip = "127.0.0." + ipCounter++;
    Node node = new Node(config, Bytes.random(64), ip, null, 30303, 30303);
    return new NodeEntry(ownerId, node);
  }

  @Test
  public void testAddNode_notFull() {
    NodeEntry entry1 = createUniqueNodeEntry();
    assertNull(bucket.addNode(entry1));
    assertEquals(1, bucket.getNodesCount());
    assertEquals(entry1, bucket.getNodes().get(0));
  }

  @Test
  public void testAddNode_duplicate() {
    NodeEntry entry1 = createUniqueNodeEntry();
    bucket.addNode(entry1);

    // Create a new entry for the same node (same IP)
    Node sameNode = new Node(config, Bytes.random(64), "127.0.0.1", null, 30303, 30303);
    NodeEntry sameEntry = new NodeEntry(ownerId, sameNode);

    assertNull(bucket.addNode(sameEntry)); // Adding the same node again
    assertEquals(1, bucket.getNodesCount());
  }

  @Test
  public void testAddNode_full() {
    // Fill the bucket to capacity
    for (int i = 0; i < KademliaOptions.BUCKET_SIZE; i++) {
      assertNull(bucket.addNode(createUniqueNodeEntry()));
    }
    assertEquals(KademliaOptions.BUCKET_SIZE, bucket.getNodesCount());

    // Adding one more should return the node to be evicted
    NodeEntry newEntry = createUniqueNodeEntry();
    NodeEntry evicted = bucket.addNode(newEntry);

    assertNotNull(evicted);
    assertEquals(KademliaOptions.BUCKET_SIZE, bucket.getNodesCount());
  }

  @Test
  public void testDropNode() {
    NodeEntry entry1 = createUniqueNodeEntry();
    NodeEntry entry2 = createUniqueNodeEntry();

    bucket.addNode(entry1);
    bucket.addNode(entry2);
    assertEquals(2, bucket.getNodesCount());

    bucket.dropNode(entry1);
    assertEquals(1, bucket.getNodesCount());
    assertEquals(entry2, bucket.getNodes().get(0));

    // Dropping non-existent node should not affect bucket
    bucket.dropNode(entry1);
    assertEquals(1, bucket.getNodesCount());
  }

  @Test
  public void testBucketDepth() {
    NodeBucket bucket5 = new NodeBucket(5);
    assertEquals(5, bucket5.getDepth());
  }

  @Test
  public void testEmptyBucket() {
    assertEquals(0, bucket.getNodesCount());
    assertEquals(0, bucket.getNodes().size());
  }
}
