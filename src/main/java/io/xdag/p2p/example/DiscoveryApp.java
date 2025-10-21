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
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.example.cli.CliConfigParser;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pure Node Discovery Testing Application
 *
 * Focused on validating Kademlia DHT node discovery without high-frequency message sending.
 * This application:
 * - Enables UDP discovery (Kademlia DHT)
 * - Monitors DHT routing table growth
 * - Logs discovery events at DEBUG level
 * - Does NOT send performance test messages
 *
 * Usage: java -cp xdagj-p2p.jar io.xdag.p2p.example.DiscoveryApp [options]
 */
@Slf4j(topic = "discovery")
public class DiscoveryApp {

  private static final long DHT_MONITOR_INITIAL_DELAY_SECONDS = 10;
  private static final long DHT_MONITOR_PERIOD_SECONDS = 30;
  private static final long MAIN_LOOP_SLEEP_MILLIS = 5000;
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private ScheduledExecutorService scheduler;
  private String nodeId;
  private long startTime;

  public static void main(String[] args) {
    DiscoveryApp app = new DiscoveryApp();
    try {
      app.start(args);
    } catch (Exception e) {
      log.error("Failed to start Discovery application: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  public void start(String[] args) throws Exception {
    // Initialize configuration
    P2pConfig config = new P2pConfig();
    P2pConstant.version = 1;

    // Parse CLI arguments
    CliConfigParser parser = new CliConfigParser();
    boolean shouldStart = parser.parseAndConfigure(args, config);
    if (!shouldStart) {
      return;
    }

    // Ensure discovery is enabled
    if (!config.isDiscoverEnable()) {
      log.warn("Discovery is disabled! Enabling it for discovery testing...");
      config.setDiscoverEnable(true);
    }

    logConfigurationSummary(config);

    // Initialize P2P service
    p2pService = new P2pService(config);
    nodeId = "node-" + config.getPort();
    eventHandler = createEventHandler();

    try {
      config.addP2pEventHandle(eventHandler);
    } catch (Exception e) {
      log.warn("Failed to register event handler: {}", e.getMessage());
    }

    // Start service
    log.info("========================================");
    log.info("Starting Pure Node Discovery Test");
    log.info("========================================");
    log.info("Mode: Kademlia DHT node discovery only");
    log.info("No performance testing messages will be sent");
    log.info("========================================");

    startTime = System.currentTimeMillis();
    p2pService.start();
    log.info("P2P service started successfully");

    // Start DHT monitoring
    initializeDhtMonitoring();

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    runMainLoop();
  }

  /**
   * Creates a minimal event handler that only logs connection events.
   * Does NOT send any test messages.
   */
  private ExampleEventHandler createEventHandler() {
    return new ExampleEventHandler(nodeId) {
      @Override
      protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
        log.info("[{}] Peer connected: {} (Total connections: {})",
                 nodeId, channel.getInetSocketAddress(), getChannels().size());
      }

      @Override
      protected void onPeerDisconnected(io.xdag.p2p.channel.Channel channel) {
        log.info("[{}] Peer disconnected: {} (Remaining connections: {})",
                 nodeId, channel.getInetSocketAddress(), getChannels().size());
      }

      @Override
      protected void onTestMessage(io.xdag.p2p.channel.Channel channel, io.xdag.p2p.example.message.TestMessage message) {
        // Log received messages but don't send any
        if (!message.isNetworkTestMessage()) {
          log.debug("[{}] Received message: {}", nodeId, message.getActualContent());
        }
      }
    };
  }

  /**
   * Initialize DHT monitoring to track node discovery progress
   */
  private void initializeDhtMonitoring() {
    scheduler = Executors.newScheduledThreadPool(1);

    // Monitor DHT routing table at regular intervals
    scheduler.scheduleAtFixedRate(
        this::logDhtStatistics,
        DHT_MONITOR_INITIAL_DELAY_SECONDS,
        DHT_MONITOR_PERIOD_SECONDS,
        TimeUnit.SECONDS);

    log.info("DHT monitoring started (interval: {}s)", DHT_MONITOR_PERIOD_SECONDS);
  }

  /**
   * Log DHT routing table statistics
   */
  private void logDhtStatistics() {
    try {
      long currentTime = System.currentTimeMillis();
      long elapsedSeconds = (currentTime - startTime) / 1000;
      int tcpConnections = eventHandler != null ? eventHandler.getChannels().size() : 0;

      Runtime runtime = Runtime.getRuntime();
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      double memoryPercent = (usedMemory * 100.0) / runtime.maxMemory();

      log.info("========================================");
      log.info("[{}] Discovery Status at {}s", nodeId, elapsedSeconds);
      log.info("========================================");
      log.info("TCP Connections: {}", tcpConnections);
      log.info("Memory: {}MB / {}MB ({}%)",
               usedMemory / (1024 * 1024),
               runtime.maxMemory() / (1024 * 1024),
               String.format("%.1f", memoryPercent));

      // Get DHT statistics if available
      if (p2pService != null) {
        try {
          List<Node> connectableNodes = p2pService.getConnectableNodes();

          log.info("Connectable Nodes: {}", connectableNodes != null ? connectableNodes.size() : 0);

          if (connectableNodes != null && !connectableNodes.isEmpty()) {
            log.info("Sample Connectable Nodes:");
            int sampleSize = Math.min(5, connectableNodes.size());
            for (int i = 0; i < sampleSize; i++) {
              Node node = connectableNodes.get(i);
              log.info("  - Node ID: {}, Address: {}",
                       node.getId() != null ? node.getId() : "null",
                       node.getPreferInetSocketAddress());
            }
          }

        } catch (Exception e) {
          log.warn("Failed to get DHT statistics: {}", e.getMessage(), e);
        }
      } else {
        log.warn("P2pService not available");
      }

      log.info("========================================");
    } catch (Exception e) {
      log.error("Fatal error in DHT monitoring task: {}", e.getMessage(), e);
    }
  }

  private void runMainLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(MAIN_LOOP_SLEEP_MILLIS);
      }
    } catch (InterruptedException e) {
      log.info("Application interrupted, shutting down...");
      Thread.currentThread().interrupt();
    }
  }

  private void shutdown() {
    log.info("Shutting down Discovery application...");

    // Stop scheduler
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Stop P2P service
    if (p2pService != null) {
      try {
        p2pService.stop();
        log.info("P2P service stopped");
      } catch (Exception e) {
        log.error("Error stopping P2P service: {}", e.getMessage());
      }
    }

    // Close connections
    if (eventHandler != null) {
      try {
        eventHandler.closeAllConnections();
        eventHandler.shutdown();
      } catch (Exception e) {
        log.warn("Error closing connections: {}", e.getMessage());
      }
    }

    log.info("Discovery application shutdown complete");
  }

  private void logConfigurationSummary(P2pConfig config) {
    log.info("=== Discovery Configuration ===");
    log.info("Port: {}", config.getPort());
    log.info("Network ID: {}", config.getNetworkId());
    log.info("Discovery enabled: {}", config.isDiscoverEnable());
    log.info("Min connections: {}", config.getMinConnections());
    log.info("Max connections: {}", config.getMaxConnections());

    if (config.getSeedNodes() != null && !config.getSeedNodes().isEmpty()) {
      log.info("TCP Seed nodes: {}", config.getSeedNodes());
    }

    if (config.getActiveNodes() != null && !config.getActiveNodes().isEmpty()) {
      log.info("UDP Active nodes: {}", config.getActiveNodes());
    }

    log.info("================================");
  }
}
