package io.xdag.p2p.example.handler;

import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.message.MessageTypes;
import io.xdag.p2p.example.message.TestMessage;
import io.xdag.p2p.utils.BytesUtils;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Base event handler for P2P examples Provides common functionality for handling connections and
 * messages
 */
@Slf4j(topic = "example")
public class ExampleEventHandler extends P2pEventHandler {

  protected final ConcurrentMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();

  public ExampleEventHandler() {
    this.messageTypes = new HashSet<>();
    this.messageTypes.add(MessageTypes.TEST.getType());
  }

  @Override
  public void onConnect(Channel channel) {
    InetSocketAddress address = channel.getInetSocketAddress();
    channels.put(address, channel);
    log.info("Connected to peer: {}", address);
    onPeerConnected(channel);
  }

  @Override
  public void onDisconnect(Channel channel) {
    InetSocketAddress address = channel.getInetSocketAddress();
    channels.remove(address);
    log.info("Disconnected from peer: {}", address);
    onPeerDisconnected(channel);
  }

  @Override
  public void onMessage(Channel channel, Bytes data) {
    try {
      byte type = data.get(0);
      byte[] messageData = BytesUtils.skip(data, 1).toArray();

      MessageTypes messageType = MessageTypes.fromByte(type);
      if (messageType == null) {
        log.warn("Unknown message type: {} from {}", type, channel.getInetSocketAddress());
        return;
      }

      switch (messageType) {
        case TEST:
          TestMessage message = new TestMessage(messageData);
          log.info(
              "Received test message from {}: {}",
              channel.getInetSocketAddress(),
              message.getContentAsString());
          onTestMessage(channel, message);
          break;
        default:
          log.warn(
              "Unhandled message type: {} from {}", messageType, channel.getInetSocketAddress());
      }
    } catch (Exception e) {
      log.error(
          "Error processing message from {}: {}",
          channel.getInetSocketAddress(),
          e.getMessage(),
          e);
    }
  }

  /**
   * Get all connected channels
   *
   * @return map of connected channels
   */
  public ConcurrentMap<InetSocketAddress, Channel> getChannels() {
    return channels;
  }

  /**
   * Send test message to all connected peers
   *
   * @param message message content
   */
  public void broadcastTestMessage(String message) {
    TestMessage testMessage = new TestMessage(message);
    Bytes messageBytes =
        Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(testMessage.getData()));

    channels
        .values()
        .forEach(
            channel -> {
              try {
                channel.send(messageBytes);
                log.info("Sent test message to {}: {}", channel.getInetSocketAddress(), message);
              } catch (Exception e) {
                log.error(
                    "Failed to send message to {}: {}",
                    channel.getInetSocketAddress(),
                    e.getMessage());
              }
            });
  }

  /** Close all connections */
  public void closeAllConnections() {
    channels
        .values()
        .forEach(
            channel -> {
              try {
                channel.close();
              } catch (Exception e) {
                log.error(
                    "Error closing channel {}: {}", channel.getInetSocketAddress(), e.getMessage());
              }
            });
    channels.clear();
  }

  /**
   * Called when a peer connects (override for custom behavior)
   *
   * @param channel the connected channel
   */
  protected void onPeerConnected(Channel channel) {
    // Override in subclasses for custom behavior
  }

  /**
   * Called when a peer disconnects (override for custom behavior)
   *
   * @param channel the disconnected channel
   */
  protected void onPeerDisconnected(Channel channel) {
    // Override in subclasses for custom behavior
  }

  /**
   * Called when a test message is received (override for custom behavior)
   *
   * @param channel the channel that received the message
   * @param message the received test message
   */
  protected void onTestMessage(Channel channel, TestMessage message) {
    // Override in subclasses for custom behavior
  }
}
