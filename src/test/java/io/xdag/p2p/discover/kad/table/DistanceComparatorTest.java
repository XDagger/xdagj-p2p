package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class DistanceComparatorTest {

  @Test
  public void testCompare() {
    P2pConfig config = new P2pConfig();

    // Use a fixed target ID for consistent testing
    byte[] targetBytes = new byte[64];
    // Set all bytes to 0x00 for predictable XOR results
    Bytes targetId = Bytes.wrap(targetBytes);

    // Node 1: Only last bit different
    byte[] id1Bytes = new byte[64];
    id1Bytes[63] = 0x01; // Only last bit set
    Node node1 = new Node(config, Bytes.wrap(id1Bytes), "127.0.0.1", null, 30301, 30301);

    // Node 2: First bit different
    byte[] id2Bytes = new byte[64];
    id2Bytes[0] = (byte) 0x80; // Only first bit set
    Node node2 = new Node(config, Bytes.wrap(id2Bytes), "127.0.0.1", null, 30302, 30302);

    // Node 3: Second-to-last bit different
    byte[] id3Bytes = new byte[64];
    id3Bytes[63] = 0x02; // Only second-to-last bit set
    Node node3 = new Node(config, Bytes.wrap(id3Bytes), "127.0.0.1", null, 30303, 30303);

    // Get actual distances to understand the real values
    int d1 = NodeEntry.distance(targetId, node1.getId());
    int d2 = NodeEntry.distance(targetId, node2.getId());
    int d3 = NodeEntry.distance(targetId, node3.getId());

    System.out.println("Actual distances:");
    System.out.println("Node1 (last bit): " + d1);
    System.out.println("Node2 (first bit): " + d2);
    System.out.println("Node3 (second-to-last bit): " + d3);
    System.out.println("BINS value: " + KademliaOptions.BINS);

    List<Node> nodes = new ArrayList<>();
    nodes.add(node2); // Should be furthest
    nodes.add(node3); // Should be middle
    nodes.add(node1); // Should be closest

    // Sort by distance
    nodes.sort(new DistanceComparator(targetId));

    // The key test is that sorting works correctly, regardless of actual distance values
    // Node1 (last bit different) should be closest
    // Node3 (second-to-last bit) should be middle
    // Node2 (first bit different) should be furthest

    // Verify that d1 < d3 < d2 (closer distances are smaller)
    assertTrue(d1 < d3, "Node1 should be closer than Node3");
    assertTrue(d3 < d2, "Node3 should be closer than Node2");

    // Verify sorted order matches distance order
    assertEquals(node1, nodes.get(0), "Closest node should be first");
    assertEquals(node3, nodes.get(1), "Middle distance node should be second");
    assertEquals(node2, nodes.get(2), "Furthest node should be last");
  }
}
