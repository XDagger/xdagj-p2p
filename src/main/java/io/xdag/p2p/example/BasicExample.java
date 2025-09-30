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
package io.xdag.p2p.example;

import io.xdag.p2p.P2pService;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.example.config.ExampleConfig;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import io.xdag.p2p.example.message.TestMessage;
import io.xdag.p2p.stats.P2pStats;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic P2P usage example
 *
 * <p>Demonstrates how to: - Configure P2P service using ExampleConfig - Register event handlers -
 * Send and receive messages - Manage connections
 */
@Slf4j(topic = "basic-example")
public class BasicExample {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;

  /** Start P2P service with basic configuration */
  public void start() {
    try {
      // Create basic configuration
      var config = ExampleConfig.basic().toP2pConfig();

      // Initialize P2P service
      p2pService = new P2pService(config);

      // Create and register event handler
      eventHandler =
          new ExampleEventHandler() {
            @Override
            protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
              log.info("Peer connected: {}", channel.getInetSocketAddress());
              // Send greeting message
              broadcastTestMessage("Hello from BasicExample!");
            }

            @Override
            protected void onTestMessage(io.xdag.p2p.channel.Channel channel, TestMessage message) {
              log.info(
                  "Received message from {}: {}",
                  channel.getInetSocketAddress(),
                  message.getContentAsString());

              // Echo the message back
              try {
                var response = new TestMessage("Echo: " + message.getContentAsString());
                // Send response logic would go here
              } catch (Exception e) {
                log.error("Failed to send response: {}", e.getMessage());
              }
            }
          };

      // registration API removed in refactor; keep handler local for demo only

      // Start the service
      log.info("Starting basic P2P service...");
      p2pService.start();
      log.info("Basic P2P service started successfully");

      // Wait for connections and send test messages
      demonstrateUsage();

    } catch (Exception e) {
      log.error("Failed to start basic P2P service: {}", e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  /** Demonstrate P2P service usage */
  private void demonstrateUsage() throws InterruptedException {
    // Wait for some connections
    Thread.sleep(5000);

    // Send test messages
    eventHandler.broadcastTestMessage("Hello P2P network!");

    // Log statistics
    logStatistics();

    // Wait a bit more
    Thread.sleep(5000);

    // Send another message
    eventHandler.broadcastTestMessage("This is a test message from BasicExample");

    // Final statistics
    logStatistics();
  }

  /** Stop the P2P service */
  public void stop() {
    log.info("Stopping basic P2P service...");

    if (eventHandler != null) {
      eventHandler.closeAllConnections();
    }

    if (p2pService != null) {
      p2pService.stop();
      log.info("Basic P2P service stopped");
    }
  }

  /**
   * Connect to a specific peer
   *
   * @param address peer address
   */
  public void connectToPeer(InetSocketAddress address) {
    if (p2pService != null) {
      p2pService.connect(address);
      log.info("Attempting to connect to peer: {}", address);
    }
  }

  /**
   * Get P2P statistics
   *
   * @return P2P statistics
   */
  public P2pStats getStatistics() {
    return p2pService != null ? p2pService.getP2pStats() : null;
  }

  /**
   * Get all discovered nodes
   *
   * @return list of all nodes
   */
  public List<Node> getAllNodes() {
    return List.of();
  }

  /**
   * Get nodes in routing table
   *
   * @return list of table nodes
   */
  public List<Node> getTableNodes() {
    return List.of();
  }

  /**
   * Get connectable nodes
   *
   * @return list of connectable nodes
   */
  public List<Node> getConnectableNodes() {
    return p2pService != null ? p2pService.getConnectableNodes() : List.of();
  }

  /** Log current statistics */
  private void logStatistics() {
    if (p2pService == null) return;

    log.info("=== Basic Example Statistics ===");
    log.info("Connected peers: {}", eventHandler.getChannels().size());
    log.info("Total nodes: {}", getAllNodes().size());
    log.info("Table nodes: {}", getTableNodes().size());
    log.info("Connectable nodes: {}", getConnectableNodes().size());
    log.info("===============================");
  }

  /** Main method for standalone execution */
  public static void main(String[] args) {
    BasicExample example = new BasicExample();

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(example::stop));

    try {
      example.start();

      // Keep running
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(1000);
      }
    } catch (InterruptedException e) {
      log.info("Example interrupted");
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Example failed: {}", e.getMessage(), e);
    } finally {
      example.stop();
    }
  }
}
