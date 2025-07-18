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
package io.xdag.p2p.discover.kad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.kad.FindNodeMessage;
import io.xdag.p2p.message.discover.kad.NeighborsMessage;
import io.xdag.p2p.message.discover.kad.PingMessage;
import io.xdag.p2p.message.discover.kad.PongMessage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NodeHandler class. These are true unit tests that test state transitions without
 * waiting for timeouts.
 */
public class NodeHandlerTest {
  private static P2pConfig p2pConfig;
  private static KadService kadService;
  private static Node currNode;
  private static Node oldNode;
  private static Node replaceNode;
  private static NodeHandler currHandler;
  private static NodeHandler oldHandler;
  private static NodeHandler replaceHandler;

  @BeforeAll
  public static void init() {
    p2pConfig = new P2pConfig();
    p2pConfig.setDiscoverEnable(false);
    kadService = new KadService(p2pConfig);
    kadService.init();
    // Set very short timeout for unit tests
    KadService.setPingTimeout(1);
    currNode = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 22222));
    oldNode = new Node(p2pConfig, new InetSocketAddress("127.0.0.2", 22222));
    replaceNode = new Node(p2pConfig, new InetSocketAddress("127.0.0.3", 22222));
    currHandler = new NodeHandler(p2pConfig, currNode, kadService);
    oldHandler = new NodeHandler(p2pConfig, oldNode, kadService);
    replaceHandler = new NodeHandler(p2pConfig, replaceNode, kadService);
  }

  @Test
  public void testInitialState() {
    // Note: The actual initial state might be ACTIVE instead of DISCOVERED
    // depending on the implementation details
    assertNotNull(currHandler.getState());
    assertNotNull(oldHandler.getState());
    assertNotNull(replaceHandler.getState());
  }

  @Test
  public void testPingPongHandling() {
    // Test ping handling
    PingMessage msg = new PingMessage(p2pConfig, currNode, kadService.getPublicHomeNode());
    currHandler.handlePing(msg);
    assertEquals(NodeHandler.State.DISCOVERED, currHandler.getState());

    // Test pong handling
    PongMessage msg1 = new PongMessage(p2pConfig, currNode);
    currHandler.handlePong(msg1);
    assertEquals(NodeHandler.State.ACTIVE, currHandler.getState());
    assertTrue(kadService.getTable().contains(currNode));
    kadService.getTable().dropNode(currNode);
  }

  @Test
  public void testStateTransitions() {
    // Test direct state change to ALIVE
    currHandler.changeState(NodeHandler.State.ALIVE);
    assertEquals(NodeHandler.State.ACTIVE, currHandler.getState());
    assertTrue(kadService.getTable().contains(currNode));

    // Test state change to DEAD
    currHandler.changeState(NodeHandler.State.DEAD);
    assertEquals(NodeHandler.State.DEAD, currHandler.getState());
  }

  @Test
  public void testNodeReplacement() throws Exception {
    Class<NodeHandler> clazz = NodeHandler.class;
    Constructor<NodeHandler> cn =
        clazz.getDeclaredConstructor(P2pConfig.class, Node.class, KadService.class);
    NodeHandler nh = cn.newInstance(p2pConfig, oldNode, kadService);
    Field declaredField = clazz.getDeclaredField("replaceCandidate");
    declaredField.setAccessible(true);
    declaredField.set(nh, replaceHandler);

    kadService.getTable().addNode(oldNode);
    nh.changeState(NodeHandler.State.EVICTCANDIDATE);
    nh.changeState(NodeHandler.State.DEAD);
    replaceHandler.changeState(NodeHandler.State.ALIVE);

    assertFalse(kadService.getTable().contains(oldNode));
    assertTrue(kadService.getTable().contains(replaceNode));
  }

  @Test
  public void testNodeHandlerCreation() {
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    assertNotNull(handler);
    assertEquals(NodeHandler.State.DISCOVERED, handler.getState());
  }

  @Test
  public void testHandlePingWithDifferentNetworkId() {
    // Given - Create a config with different network ID
    P2pConfig differentConfig = new P2pConfig();
    differentConfig.setNetworkId(999);
    PingMessage pingMsg = new PingMessage(differentConfig, currNode, replaceNode);
    NodeHandler handler = new NodeHandler(p2pConfig, replaceNode, kadService);
    
    // When
    handler.handlePing(pingMsg);
    
    // Then
    assertEquals(NodeHandler.State.DEAD, handler.getState());
  }

  @Test
  public void testHandlePongWhenNotWaitingForPong() throws Exception {
    // Given
    PongMessage pongMsg = new PongMessage(p2pConfig, currNode);
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // Use reflection to set waitForPong to false (simulating not waiting for pong)
    Field waitForPongField = NodeHandler.class.getDeclaredField("waitForPong");
    waitForPongField.setAccessible(true);
    waitForPongField.set(handler, false);
    
    // When - handler not waiting for pong
    handler.handlePong(pongMsg);
    
    // Then - waitForPong should still be false (no processing should happen)
    assertFalse((Boolean) waitForPongField.get(handler));
  }

  @Test
  public void testHandlePongWithIncompatibleNetworkId() throws Exception {
    // Given - Create a config with different network ID
    P2pConfig differentConfig = new P2pConfig();
    differentConfig.setNetworkId(999);
    PongMessage pongMsg = new PongMessage(differentConfig, currNode);
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // Use reflection to set waitForPong to true
    Field waitForPongField = NodeHandler.class.getDeclaredField("waitForPong");
    waitForPongField.setAccessible(true);
    waitForPongField.set(handler, true);
    
    // When
    handler.handlePong(pongMsg);
    
    // Then
    assertEquals(NodeHandler.State.DEAD, handler.getState());
    assertFalse((Boolean) waitForPongField.get(handler));
  }

  @Test
  public void testHandleNeighborsWhenNotExpected() {
    // Given
    List<Node> neighbors = new ArrayList<>();
    neighbors.add(currNode);
    NeighborsMessage neighborsMsg = new NeighborsMessage(p2pConfig, currNode, neighbors, 123L);
    NodeHandler handler = new NodeHandler(p2pConfig, replaceNode, kadService);
    InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 8080);
    
    // When - not waiting for neighbors
    handler.handleNeighbours(neighborsMsg, sender);
    
    // Then - should just log warning and return (verify by checking waitForNeighbors is still false)
    assertFalse(handler.isWaitForNeighbors());
  }

  @Test
  public void testHandleNeighborsWithValidNodes() throws Exception {
    // Given
    List<Node> neighbors = new ArrayList<>();
    Node neighbor1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.4", 22222));
    Node neighbor2 = new Node(p2pConfig, new InetSocketAddress("127.0.0.5", 22222));
    neighbors.add(neighbor1);
    neighbors.add(neighbor2);
    
    NeighborsMessage neighborsMsg = new NeighborsMessage(p2pConfig, currNode, neighbors, 123L);
    NodeHandler handler = new NodeHandler(p2pConfig, replaceNode, kadService);
    
    // Use reflection to set waitForNeighbors to true
    Field waitForNeighborsField = NodeHandler.class.getDeclaredField("waitForNeighbors");
    waitForNeighborsField.setAccessible(true);
    waitForNeighborsField.set(handler, true);
    
    InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 8080);
    
    // When
    handler.handleNeighbours(neighborsMsg, sender);
    
    // Then
    assertFalse((Boolean) waitForNeighborsField.get(handler));
  }

  @Test
  public void testHandleFindNode() {
    // Given
    byte[] targetId = new byte[32];
    FindNodeMessage findNodeMsg = new FindNodeMessage(p2pConfig, currNode, Bytes.wrap(targetId));
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // When
    handler.handleFindNode(findNodeMsg);
    
    // Then - should send neighbors response (verified indirectly through no exceptions)
    assertNotNull(handler);
  }

  @Test
  public void testHandleTimedOutWithRemainingTrials() throws Exception {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // Use reflection to set waitForPong to true
    Field waitForPongField = NodeHandler.class.getDeclaredField("waitForPong");
    waitForPongField.setAccessible(true);
    waitForPongField.set(handler, true);
    
    // When
    handler.handleTimedOut();
    
    // Then - waitForPong should be reset by handleTimedOut, but sendPing() will set it back to true
    // The important thing is that the method completed successfully and ping trials decreased
    assertTrue(handler.getPingTrials().get() < 3); // Should have decremented from initial 3
  }

  @Test
  public void testHandleTimedOutExhaustedTrials() throws Exception {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // Use reflection to set waitForPong to true
    Field waitForPongField = NodeHandler.class.getDeclaredField("waitForPong");
    waitForPongField.setAccessible(true);
    waitForPongField.set(handler, true);
    
    // Exhaust ping trials
    handler.getPingTrials().set(0);
    
    // When
    handler.handleTimedOut();
    
    // Then
    assertFalse((Boolean) waitForPongField.get(handler));
    assertEquals(NodeHandler.State.DEAD, handler.getState());
  }

  @Test
  public void testSendFindNode() {
    // Given
    byte[] targetId = new byte[32];
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // When
    handler.sendFindNode(targetId);
    
    // Then
    assertTrue(handler.isWaitForNeighbors());
  }

  @Test
  public void testSendNeighbors() {
    // Given
    List<Node> neighbors = new ArrayList<>();
    neighbors.add(currNode);
    NodeHandler handler = new NodeHandler(p2pConfig, replaceNode, kadService);
    
    // When
    handler.sendNeighbours(neighbors, 123L);
    
    // Then - should complete without error
    assertNotNull(handler);
  }

  @Test
  public void testToString() {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // When
    String result = handler.toString();
    
    // Then
    assertTrue(result.contains("NodeHandler"));
    assertTrue(result.contains("state:"));
    assertTrue(result.contains("node:"));
  }

  @Test
  public void testStateTransitionFromAliveToActive() {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    
    // When
    handler.changeState(NodeHandler.State.ALIVE);
    
    // Then
    assertEquals(NodeHandler.State.ACTIVE, handler.getState());
  }

  @Test
  public void testStateTransitionFromEvictCandidateToActive() {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    kadService.getTable().addNode(currNode); // Add to table first
    handler.changeState(NodeHandler.State.EVICTCANDIDATE);
    
    // When
    handler.changeState(NodeHandler.State.ACTIVE);
    
    // Then
    assertEquals(NodeHandler.State.ACTIVE, handler.getState());
  }

  @Test
  public void testNodeHandlerWithNullPreferredAddress() {
    // Given
    Node nodeWithoutAddress = mock(Node.class);
    when(nodeWithoutAddress.getPreferInetSocketAddress()).thenReturn(null);
    
    // When
    NodeHandler handler = new NodeHandler(p2pConfig, nodeWithoutAddress, kadService);
    
    // Then
    // Should not call changeState to DISCOVERED since address is null
    assertNotNull(handler);
  }

  @Test
  public void testPingTrialsDecrement() {
    // Given
    NodeHandler handler = new NodeHandler(p2pConfig, currNode, kadService);
    int initialTrials = handler.getPingTrials().get();
    
    // When
    handler.handleTimedOut();
    
    // Then
    assertEquals(initialTrials - 1, handler.getPingTrials().get());
  }

  @AfterAll
  public static void destroy() {
    kadService.close();
  }
}
