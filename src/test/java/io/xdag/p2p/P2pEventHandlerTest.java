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
package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for P2pEventHandler abstract class. Tests the basic functionality and default
 * implementations.
 */
public class P2pEventHandlerTest {

  @Test
  public void testEventHandlerCreation() {
    TestEventHandler handler = new TestEventHandler();
    assertNotNull(handler);
  }

  @Test
  public void testMessageTypes() {
    TestEventHandler handler = new TestEventHandler();
    Set<Byte> messageTypes = new HashSet<>();
    messageTypes.add((byte) 0x01);
    messageTypes.add((byte) 0x02);
    handler.setMessageTypes(messageTypes);

    assertEquals(2, handler.getMessageTypes().size());
    assertTrue(handler.getMessageTypes().contains((byte) 0x01));
    assertTrue(handler.getMessageTypes().contains((byte) 0x02));
  }

  @Test
  public void testDefaultMethods() {
    TestEventHandler handler = new TestEventHandler();
    Bytes testData = Bytes.fromHexString("0x1234");

    // Should not throw exception - default implementations are empty
    handler.onConnect(null);
    handler.onDisconnect(null);
    handler.onMessage(null, testData);
  }

  @Test
  public void testOverriddenMethods() {
    OverriddenEventHandler handler = new OverriddenEventHandler();
    Bytes testData = Bytes.fromHexString("0x1234");

    handler.onConnect(null);
    assertEquals(1, handler.connectCount);

    handler.onDisconnect(null);
    assertEquals(1, handler.disconnectCount);

    handler.onMessage(null, testData);
    assertEquals(1, handler.messageCount);
  }

  @Test
  public void testMessageTypesInitialization() {
    TestEventHandler handler = new TestEventHandler();
    // Initially, messageTypes might be null
    handler.setMessageTypes(new HashSet<>());
    assertNotNull(handler.getMessageTypes());
    assertEquals(0, handler.getMessageTypes().size());
  }

  /** Test implementation of P2pEventHandler for testing purposes. */
  private static class TestEventHandler extends P2pEventHandler {
    public void setMessageTypes(Set<Byte> messageTypes) {
      this.messageTypes = messageTypes;
    }
  }

  /** Test implementation with overridden methods for testing. */
  private static class OverriddenEventHandler extends P2pEventHandler {
    int connectCount = 0;
    int disconnectCount = 0;
    int messageCount = 0;

    @Override
    public void onConnect(io.xdag.p2p.channel.Channel channel) {
      connectCount++;
    }

    @Override
    public void onDisconnect(io.xdag.p2p.channel.Channel channel) {
      disconnectCount++;
    }

    @Override
    public void onMessage(io.xdag.p2p.channel.Channel channel, Bytes data) {
      messageCount++;
    }
  }
}
