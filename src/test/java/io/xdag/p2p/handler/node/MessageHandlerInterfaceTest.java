package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.message.node.Message;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MessageHandler interface. Tests the interface contract and basic functionality.
 */
class MessageHandlerInterfaceTest {

  @Test
  void testMessageHandlerInterface() {
    // Given
    MessageHandler handler =
        new MessageHandler() {
          @Override
          public void onMessage(Channel channel, Message message) {
            // Test implementation
          }

          @Override
          public void onConnect(Channel channel) {
            // Test implementation
          }

          @Override
          public void onDisconnect(Channel channel) {
            // Test implementation
          }
        };

    Channel mockChannel = mock(Channel.class);
    Message mockMessage = mock(Message.class);

    // When & Then
    assertNotNull(handler);

    // Should not throw exceptions
    handler.onMessage(mockChannel, mockMessage);
    handler.onConnect(mockChannel);
    handler.onDisconnect(mockChannel);
  }
}
