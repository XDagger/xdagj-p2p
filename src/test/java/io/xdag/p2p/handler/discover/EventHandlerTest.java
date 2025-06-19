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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Unit tests for EventHandler interface. Tests the interface contract and basic functionality. */
class EventHandlerTest {

  @Test
  void testEventHandlerInterface() {
    // Given
    EventHandler handler =
        new EventHandler() {
          @Override
          public void channelActivated() {
            // Test implementation
          }

          @Override
          public void handleEvent(UdpEvent event) {
            // Test implementation
          }

          @Override
          public void setMessageSender(Consumer<UdpEvent> messageSender) {
            // Test implementation
          }
        };

    UdpEvent mockEvent = mock(UdpEvent.class);
    @SuppressWarnings("unchecked")
    Consumer<UdpEvent> mockConsumer = mock(Consumer.class);

    // When & Then
    assertNotNull(handler);

    // Should not throw exceptions
    handler.channelActivated();
    handler.handleEvent(mockEvent);
    handler.setMessageSender(mockConsumer);
  }
}
