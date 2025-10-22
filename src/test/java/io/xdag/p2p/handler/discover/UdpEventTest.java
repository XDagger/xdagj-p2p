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
package io.xdag.p2p.handler.discover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import io.xdag.p2p.message.Message;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** Unit tests for UdpEvent class. Tests event creation, getters, and setters. */
class UdpEventTest {

  @Test
  void testDefaultConstructor() {
    // Given & When
    UdpEvent event = new UdpEvent();

    // Then
    assertNotNull(event);
    assertNull(event.getMessage());
    assertNull(event.getAddress());
  }

  @Test
  void testParameterizedConstructor() {
    // Given
    Message mockMessage = mock(Message.class);
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);

    // When
    UdpEvent event = new UdpEvent(mockMessage, address);

    // Then
    assertNotNull(event);
    assertEquals(mockMessage, event.getMessage());
    assertEquals(address, event.getAddress());
  }

  @Test
  void testSettersAndGetters() {
    // Given
    UdpEvent event = new UdpEvent();
    Message mockMessage = mock(Message.class);
    InetSocketAddress address = new InetSocketAddress("192.168.1.1", 9090);

    // When
    event.setMessage(mockMessage);
    event.setAddress(address);

    // Then
    assertEquals(mockMessage, event.getMessage());
    assertEquals(address, event.getAddress());
  }

  @Test
  void testSetNullValues() {
    // Given
    Message mockMessage = mock(Message.class);
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
    UdpEvent event = new UdpEvent(mockMessage, address);

    // When
    event.setMessage(null);
    event.setAddress(null);

    // Then
    assertNull(event.getMessage());
    assertNull(event.getAddress());
  }
}
