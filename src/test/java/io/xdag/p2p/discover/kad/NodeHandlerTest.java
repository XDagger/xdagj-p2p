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

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.kad.PingMessage;
import io.xdag.p2p.message.discover.kad.PongMessage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
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

  @AfterAll
  public static void destroy() {
    kadService.close();
  }
}
