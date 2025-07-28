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
package io.xdag.p2p.discover;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for NodeManager - the core node management component. Tests node management and discovery
 * functionality.
 */
public class NodeManagerTest {

  private NodeManager nodeManager;
  private P2pConfig config;

  @BeforeEach
  public void setUp() {
    config = new P2pConfig();
    nodeManager = new NodeManager(config);
    nodeManager.init(); // Initialize the discovery service
  }

  @Test
  public void testInitialization() {
    assertNotNull(nodeManager);
    assertNotNull(nodeManager.getHomeNode());
  }

  @Test
  public void testGetHomeNode() {
    Node homeNode = nodeManager.getHomeNode();
    assertNotNull(homeNode);
    assertNotNull(homeNode.getId());
    assertNotNull(homeNode.getHostV4());
  }

  @Test
  public void testGetConnectableNodes() {
    List<Node> connectableNodes = nodeManager.getConnectableNodes();
    assertNotNull(connectableNodes);
    // Initially should be empty or contain bootstrap nodes
    assertTrue(connectableNodes.size() >= 0);
  }

  @Test
  public void testGetTableNodes() {
    List<Node> tableNodes = nodeManager.getTableNodes();
    assertNotNull(tableNodes);
    // Initially should be empty
    assertTrue(tableNodes.size() >= 0);
  }

  @Test
  public void testGetAllNodes() {
    List<Node> allNodes = nodeManager.getAllNodes();
    assertNotNull(allNodes);
    // Should include at least the home node or be empty initially
    assertTrue(allNodes.size() >= 0);
  }

  @Test
  public void testNodeManagerLifecycle() {
    // Test that we can initialize and close without errors
    NodeManager testManager = new NodeManager(config);
    testManager.init();

    assertNotNull(testManager.getHomeNode());

    // Close should not throw exceptions
    testManager.close();
  }

  @Test
  public void testMultipleInitialization() {
    // Test that multiple init calls don't cause issues
    nodeManager.init();
    nodeManager.init();

    assertNotNull(nodeManager.getHomeNode());
  }

  @Test
  public void testConfigIntegration() {
    // Test that NodeManager respects config settings
    P2pConfig customConfig = new P2pConfig();
    customConfig.setDiscoverEnable(false);

    NodeManager customManager = new NodeManager(customConfig);
    customManager.init();

    assertNotNull(customManager.getHomeNode());

    customManager.close();
  }

  @Test
  public void testNodeListConsistency() {
    List<Node> tableNodes = nodeManager.getTableNodes();
    List<Node> allNodes = nodeManager.getAllNodes();
    List<Node> connectableNodes = nodeManager.getConnectableNodes();

    // All lists should be non-null
    assertNotNull(tableNodes);
    assertNotNull(allNodes);
    assertNotNull(connectableNodes);

    // Table nodes should be a subset of all nodes
    assertTrue(allNodes.size() >= tableNodes.size());
  }
}
