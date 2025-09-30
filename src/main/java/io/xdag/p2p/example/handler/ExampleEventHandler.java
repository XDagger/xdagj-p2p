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
package io.xdag.p2p.example.handler;

import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.message.MessageTypes;
import io.xdag.p2p.example.message.TestMessage;
import io.xdag.p2p.utils.BytesUtils;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Base event handler for P2P examples Provides common functionality for handling connections and
 * messages
 */
@Slf4j(topic = "example")
public class ExampleEventHandler extends P2pEventHandler {

  @Getter
  protected final ConcurrentMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
  
  // Network test statistics
  protected final ConcurrentMap<String, Long> messageFirstReceived = new ConcurrentHashMap<>();
  protected final AtomicInteger totalReceived = new AtomicInteger(0);
  protected final AtomicInteger totalForwarded = new AtomicInteger(0);
  protected final AtomicInteger duplicatesReceived = new AtomicInteger(0);
  protected final AtomicLong totalLatency = new AtomicLong(0);

  @Getter
  protected final String nodeId;

  public ExampleEventHandler() {
    this.nodeId = generateNodeId();
    this.messageTypes = new HashSet<>();
    this.messageTypes.add(MessageTypes.TEST.getType());
  }

  public ExampleEventHandler(String nodeId) {
    this.nodeId = nodeId != null ? nodeId : generateNodeId();
    this.messageTypes = new HashSet<>();
    this.messageTypes.add(MessageTypes.TEST.getType());
  }

  private String generateNodeId() {
    return "node-" + UUID.randomUUID().toString().substring(0, 8);
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
          if (message.isNetworkTestMessage()) {
            handleNetworkTestMessage(channel, message);
          } else {
            log.info(
                "Received test message from {}: {}",
                channel.getInetSocketAddress(),
                message.getContentAsString());
          }
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
   * Send test message to all connected peers
   *
   * @param message message content
   */
  public void broadcastTestMessage(String message) {
    TestMessage testMessage = new TestMessage(message);
    Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(testMessage.getData()));
    io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

    channels
        .values()
        .forEach(
            channel -> {
              try {
                channel.send(networkMsg);
                log.info("Sent test message to {}: {}", channel.getInetSocketAddress(), message);
              } catch (Exception e) {
                log.error(
                    "Failed to send message to {}: {}",
                    channel.getInetSocketAddress(),
                    e.getMessage());
              }
            });
  }

  /**
   * Send network test message to all connected peers
   *
   * @param testType Test type identifier
   * @param content Message content
   * @param maxHops Maximum hop count
   */
  public void sendNetworkTestMessage(String testType, String content, int maxHops) {
    String messageId = UUID.randomUUID().toString().substring(0, 8);
    TestMessage testMessage = new TestMessage(messageId, nodeId, System.currentTimeMillis(), 
                                            0, maxHops, testType, content);
    
    Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(testMessage.getData()));
    io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

    log.info("Sending network test message: {} (type: {}, maxHops: {})", 
             messageId, testType, maxHops);

    channels
        .values()
        .forEach(
            channel -> {
              try {
                    channel.send(networkMsg);
                log.debug("Sent network test message to {}: {}", 
                         channel.getInetSocketAddress(), messageId);
              } catch (Exception e) {
                log.error(
                    "Failed to send network test message to {}: {}",
                    channel.getInetSocketAddress(),
                    e.getMessage());
              }
            });
  }

  /**
   * Handle received network test message
   *
   * @param channel the channel that received the message
   * @param message the received network test message
   */
  protected void handleNetworkTestMessage(Channel channel, TestMessage message) {
    try {
      String messageId = message.getMessageId();
      long receiveTime = System.currentTimeMillis();
      
      log.debug("Received network test message: {} from {} (hops: {})", 
               messageId, channel.getInetSocketAddress(), message.getHopCount());
      
      // Track statistics
      Long firstReceived = messageFirstReceived.get(messageId);
      if (firstReceived == null) {
        // First time receiving this message
        messageFirstReceived.put(messageId, receiveTime);
        
        totalReceived.incrementAndGet();
        long latency = receiveTime - message.getCreateTime();
        totalLatency.addAndGet(latency);
        
        log.info("Network test message received: {} (hops: {}, latency: {}ms, sender: {})", 
                messageId, message.getHopCount(), latency, message.getOriginSender());
        
        // Forward message if not expired and not from this node
        if (!message.isExpired() && !message.getOriginSender().equals(nodeId)) {
          forwardNetworkTestMessage(message);
        }
        
      } else {
        // Duplicate message
        duplicatesReceived.incrementAndGet();
        log.debug("Duplicate network test message: {} (received {} times)", 
                 messageId, duplicatesReceived.get());
      }
      
    } catch (Exception e) {
      log.error("Error handling network test message: {}", e.getMessage(), e);
    }
  }

  /**
   * Forward a network test message to connected peers
   *
   * @param originalMessage Original message to forward
   */
  protected void forwardNetworkTestMessage(TestMessage originalMessage) {
    try {
      TestMessage forwardCopy = originalMessage.createForwardCopy(nodeId);
      
      if (forwardCopy != null && !forwardCopy.isExpired()) {
        Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(forwardCopy.getData()));
        io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

        totalForwarded.incrementAndGet();
        
        log.debug("Forwarding network test message: {} (hops: {}/{})", 
                 forwardCopy.getMessageId(), forwardCopy.getHopCount(), forwardCopy.getMaxHops());

        channels
            .values()
            .forEach(
                channel -> {
                  try {
                    channel.send(networkMsg);
                  } catch (Exception e) {
                    log.error(
                        "Failed to forward network test message to {}: {}",
                        channel.getInetSocketAddress(),
                        e.getMessage());
                  }
                });
      }
      
    } catch (Exception e) {
      log.error("Error forwarding network test message: {}", e.getMessage(), e);
    }
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

  /**
   * Get network test statistics
   *
   * @return Statistics summary string
   */
  public String getNetworkTestStatistics() {
    double avgLatency = totalReceived.get() > 0 ? 
        (double) totalLatency.get() / totalReceived.get() : 0.0;
    
    return String.format(
        "Node %s - Received: %d, Forwarded: %d, Duplicates: %d, AvgLatency: %.2fms, UniqueMessages: %d",
        nodeId, 
        totalReceived.get(),
        totalForwarded.get(), 
        duplicatesReceived.get(),
        avgLatency,
        messageFirstReceived.size()
    );
  }

  /**
   * Reset network test statistics
   */
  public void resetNetworkTestStatistics() {
    messageFirstReceived.clear();
    totalReceived.set(0);
    totalForwarded.set(0);
    duplicatesReceived.set(0);
    totalLatency.set(0);
    log.info("Network test statistics reset for node: {}", nodeId);
  }
}
