package io.xdag.p2p.handler.discover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import io.xdag.p2p.message.discover.Message;
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
