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
