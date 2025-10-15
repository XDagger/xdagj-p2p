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
import io.xdag.p2p.example.cli.CliConfigParser;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application for starting P2P service with CLI configuration and network testing
 *
 * <p>Features:
 * - CLI argument parsing and configuration
 * - Intensive network stress testing
 * - Real-time performance monitoring
 * - Graceful shutdown handling
 */
@Slf4j(topic = "app")
public class StartApp {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private ScheduledExecutorService scheduler;
  private String nodeId;
  private boolean enableDetailedLogging;
  private long startTime;

  // TPS measurement counters (for non-logging mode)
  private final java.util.concurrent.atomic.AtomicLong messageCounter = new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong lastCounterSnapshot = new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong lastCounterTime = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

  public static void main(String[] args) {
    StartApp app = new StartApp();
    try {
      app.start(args);
    } catch (Exception e) {
      log.error("Failed to start P2P application: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  public void start(String[] args) throws Exception {
    // Initialize configuration
    P2pConfig config = new P2pConfig();
    P2pConstant.version = 1;

    // Parse command line arguments
    CliConfigParser parser = new CliConfigParser();
    boolean shouldStart = parser.parseAndConfigure(args, config);

    if (!shouldStart) {
      return; // Help was printed, exit gracefully
    }

    // Check if detailed logging should be enabled (default: true)
    enableDetailedLogging = !"false".equalsIgnoreCase(System.getenv("ENABLE_DETAILED_LOGGING"));
    log.info("Detailed logging: {}", enableDetailedLogging ? "ENABLED" : "DISABLED (Maximum TPS mode)");

    logConfigurationSummary(config);

    // Initialize P2P service and event handler
    p2pService = new P2pService(config);
    nodeId = "node-" + config.getPort();
    eventHandler = createEventHandler();
    // register example handler to receive connect/message callbacks
    try {
      config.addP2pEventHandle(eventHandler);
    } catch (Exception e) {
      log.warn("Failed to register event handler: {}", e.getMessage());
    }

    // Start the service
    log.info("Starting P2P service...");
    startTime = System.currentTimeMillis(); // Record start time for TPS calculation
    p2pService.start();
    log.info("P2P service started successfully");

    // Initialize and schedule network testing
    initializeNetworkTesting();

    // Add shutdown hook and run main loop
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    runMainLoop();
  }

  private ExampleEventHandler createEventHandler() {
    return new ExampleEventHandler(nodeId) {
      @Override
      protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
        // Simple connection log
        log.info("[{}] Connected: {} (Total: {})",
                 nodeId,
                 channel.getInetSocketAddress(),
                 getChannels().size());

        broadcastTestMessage("Welcome to the P2P network from " + nodeId + "!");

        // Start testing when we have multiple connections
        if (getChannels().size() >= 2) {
          sendNetworkTestMessage("connection_test", "Testing new connection from " + nodeId, 6);
        }
      }

      @Override
      protected void onPeerDisconnected(io.xdag.p2p.channel.Channel channel) {
        // Simple disconnection log
        log.info("[{}] Disconnected: {} (Remaining: {})",
                 nodeId,
                 channel.getInetSocketAddress(),
                 getChannels().size());
      }

      @Override
      protected void onTestMessage(io.xdag.p2p.channel.Channel channel, io.xdag.p2p.example.message.TestMessage message) {
        if (message.isNetworkTestMessage()) {
          // Always count messages for TPS measurement
          messageCounter.incrementAndGet();

          if (enableDetailedLogging) {
            long timestamp = System.currentTimeMillis();
            int messageSize = message.getData().length;
            log.info("MSG_RECEIVED|{}|{}|{}|{}|{}|{}|{}|{}|{}",
                    timestamp, nodeId, message.getMessageId(), message.getOriginSender(),
                    message.getHopCount(), message.getMaxHops(), message.getAge(), message.getTestType(), messageSize);
          }
        } else {
          log.info("Node {}: Received regular message from {}: {}",
                  nodeId, channel.getInetSocketAddress(), message.getActualContent());
        }
      }

      @Override
      protected void forwardNetworkTestMessage(io.xdag.p2p.example.message.TestMessage originalMessage) {
        super.forwardNetworkTestMessage(originalMessage);
        if (enableDetailedLogging && !originalMessage.isExpired()) {
          long timestamp = System.currentTimeMillis();
          int messageSize = originalMessage.getData().length;
          log.info("MSG_FORWARDED|{}|{}|{}|{}|{}|{}|{}",
                  timestamp, nodeId, originalMessage.getMessageId(), originalMessage.getOriginSender(),
                  originalMessage.getHopCount() + 1, getChannels().size(), messageSize);
        }
      }
    };
  }

  private void initializeNetworkTesting() {
    // Check if EXTREME_TPS mode is enabled
    boolean extremeTpsMode = "true".equalsIgnoreCase(System.getenv("EXTREME_TPS_MODE"));

    if (extremeTpsMode) {
      log.warn("======================================");
      log.warn("EXTREME TPS MODE ENABLED!");
      log.warn("WARNING: This will generate MAXIMUM message load");
      log.warn("System may experience high CPU/memory usage");
      log.warn("Use for stress testing and capacity planning only");
      log.warn("======================================");

      // Use massive thread pool for extreme concurrency
      scheduler = Executors.newScheduledThreadPool(32);

      // EXTREME MODE: Remove ALL delays, send at maximum possible rate
      // Each thread continuously sends messages without any sleep
      for (int i = 0; i < 16; i++) {  // 16 concurrent senders per node
        scheduler.submit(this::extremeTpsSender);
      }

      // Monitoring only (no rate limiting)
      // Unified performance monitoring: Output every 5 seconds with all key metrics
      scheduler.scheduleAtFixedRate(this::logTpsCounterStatistics, 5, 5, TimeUnit.SECONDS);

    } else {
      // NORMAL MODE: Controlled rate with delays
      scheduler = Executors.newScheduledThreadPool(8);

      // High-frequency TPS-focused testing
      scheduler.scheduleAtFixedRate(this::performHighFrequencyTpsTest, 5, 100, TimeUnit.MILLISECONDS); // 10 Hz
      scheduler.scheduleAtFixedRate(this::performBurstTpsTest, 10, 250, TimeUnit.MILLISECONDS); // 4 Hz burst

      // Medium-frequency comprehensive tests
      scheduler.scheduleAtFixedRate(this::performNetworkTests, 10, 1, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(this::performBurstTests, 30, 5, TimeUnit.SECONDS);
      scheduler.scheduleAtFixedRate(this::performStabilityTests, 60, 15, TimeUnit.SECONDS);

      // Low-frequency analysis and monitoring
      scheduler.scheduleAtFixedRate(this::performNetworkAnalysisTests, 90, 30, TimeUnit.SECONDS);

      // TPS measurement for no-logging mode
      if (!enableDetailedLogging) {
        scheduler.scheduleAtFixedRate(this::logTpsCounterStatistics, 5, 5, TimeUnit.SECONDS);
      }
    }
  }

  private void runMainLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(5000); // Reduced frequency for better performance
      }
    } catch (InterruptedException e) {
      log.info("Application interrupted, shutting down...");
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Check if network testing should be performed
   * @return true if eventHandler exists and has active connections
   */
  private boolean canPerformNetworkTest() {
    return eventHandler != null && !eventHandler.getChannels().isEmpty();
  }

  /**
   * Execute network test with common error handling
   */
  private void executeNetworkTest(String testType, Runnable testAction) {
    if (!canPerformNetworkTest()) {
      return;
    }
    
    try {
      log.info("Node {}: Performing {} with {} connections", 
              nodeId, testType, eventHandler.getChannels().size());
      testAction.run();
    } catch (Exception e) {
      log.warn("Error performing {}: {}", testType, e.getMessage());
    }
  }

  private void performNetworkTests() {
    executeNetworkTest("periodic network tests", () -> {
      // Increase test intensity for professional testing
      eventHandler.sendNetworkTestMessage("latency_test", "Periodic latency test from " + nodeId, 8);
      eventHandler.sendNetworkTestMessage("throughput_test", "Throughput test from " + nodeId, 6);
      eventHandler.sendNetworkTestMessage("coverage_test", "Network coverage test from " + nodeId, 10);

      // Add new professional test types
      eventHandler.sendNetworkTestMessage("route_discovery", "Route discovery test from " + nodeId, 12);
      eventHandler.sendNetworkTestMessage("congestion_test", "Network congestion test from " + nodeId, 5);
    });
  }

  /**
   * High-frequency TPS test - optimized for maximum throughput measurement
   * Sends multiple small messages rapidly to measure TPS limits
   */
  private void performHighFrequencyTpsTest() {
    if (!canPerformNetworkTest()) {
      return;
    }

    try {
      // Send 5 quick messages per execution (10 Hz × 5 = 50 msg/s per node)
      for (int i = 0; i < 5; i++) {
        eventHandler.sendNetworkTestMessage("tps_test", "TPS-" + i + "-" + System.nanoTime(), 4);
      }
    } catch (Exception e) {
      // Silent failure to avoid log spam
    }
  }

  /**
   * Burst TPS test - sends larger batches at moderate frequency
   * Tests system's ability to handle burst loads
   */
  private void performBurstTpsTest() {
    if (!canPerformNetworkTest()) {
      return;
    }

    try {
      // Send 20 messages in burst (4 Hz × 20 = 80 msg/s per node)
      for (int i = 0; i < 20; i++) {
        eventHandler.sendNetworkTestMessage("burst_tps", "Burst-" + i + "-" + System.nanoTime(), 5);
      }
    } catch (Exception e) {
      // Silent failure to avoid log spam
    }
  }

  private void performBurstTests() {
    executeNetworkTest("burst pressure tests", () -> {
      // Increase burst intensity for stress testing
      for (int i = 0; i < 10; i++) { // Increased from 5 to 10
        eventHandler.sendNetworkTestMessage("burst_test", "Burst test #" + i + " from " + nodeId, 4);
        eventHandler.sendNetworkTestMessage("pressure_test", "Pressure test #" + i + " from " + nodeId, 6);
        
        // Add variable message sizes for comprehensive testing
        String variableSizeContent = "VariableSize-".repeat(Math.max(1, i * 10));
        eventHandler.sendNetworkTestMessage("size_test", variableSizeContent, 5);
        
        try {
          Thread.sleep(50); // Reduced delay for higher intensity
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  private void performStabilityTests() {
    executeNetworkTest("stability tests", () -> {
      // Create variable large content for comprehensive stability testing
      String largeContent = "StabilityTest-".repeat(100); // Increased from 50 to 100
      String mediumContent = "MediumTest-".repeat(25);
      
      eventHandler.sendNetworkTestMessage("stability_test", largeContent, 8);
      eventHandler.sendNetworkTestMessage("reliability_test", "Long-term reliability test from " + nodeId, 10);
      eventHandler.sendNetworkTestMessage("resilience_test", "Network resilience test from " + nodeId, 5);
      
      // Add new stability test types
      eventHandler.sendNetworkTestMessage("endurance_test", mediumContent, 7);
      eventHandler.sendNetworkTestMessage("recovery_test", "Recovery test from " + nodeId, 15);
      eventHandler.sendNetworkTestMessage("fault_tolerance", "Fault tolerance test from " + nodeId, 6);
    });
  }

  /**
   * Perform comprehensive network analysis tests
   */
  private void performNetworkAnalysisTests() {
    executeNetworkTest("network analysis tests", () -> {
      // Network topology discovery
      eventHandler.sendNetworkTestMessage("topology_scan", "Network topology scan from " + nodeId, 20);
      
      // Performance benchmarking
      long timestamp = System.currentTimeMillis();
      eventHandler.sendNetworkTestMessage("benchmark_test", "Benchmark-" + timestamp + "-" + nodeId, 8);
      
      // Route efficiency testing
      eventHandler.sendNetworkTestMessage("route_efficiency", "Route efficiency test from " + nodeId, 15);
    });
  }

  /**
   * Unified performance monitoring log - Simple one-line output
   * Consolidates all statistics to avoid log clutter
   */
  private void logTpsCounterStatistics() {
    long currentCount = messageCounter.get();
    long currentTime = System.currentTimeMillis();
    long lastCount = lastCounterSnapshot.get();
    long lastTime = lastCounterTime.get();

    long messagesDelta = currentCount - lastCount;
    long timeDelta = currentTime - lastTime;

    if (timeDelta > 0) {
      double intervalTps = (messagesDelta * 1000.0) / timeDelta;
      long elapsedSeconds = (currentTime - startTime) / 1000;
      int connections = eventHandler != null ? eventHandler.getChannels().size() : 0;

      // Get memory information
      Runtime runtime = Runtime.getRuntime();
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      double memoryPercent = (usedMemory * 100.0) / runtime.maxMemory();

      // Simple one-line format
      log.info("[{}] Uptime: {}s | TPS: {} | Messages: {} | Connections: {} | Memory: {}/{}MB ({}%)",
               nodeId,
               elapsedSeconds,
               String.format("%.0f", intervalTps),
               String.format("%,d", currentCount),
               connections,
               usedMemory / (1024 * 1024),
               runtime.maxMemory() / (1024 * 1024),
               String.format("%.1f", memoryPercent));

      // Update snapshots
      lastCounterSnapshot.set(currentCount);
      lastCounterTime.set(currentTime);
    }
  }

  private void shutdown() {
    log.info("Shutting down P2P application...");

    // Stop scheduler first to prevent new messages
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Stop P2P service BEFORE closing connections
    // This sets ChannelManager.isShutdown() flag, preventing ban during close
    if (p2pService != null) {
      try {
        p2pService.stop();
        log.info("P2P service stopped");
      } catch (Exception e) {
        log.error("Error stopping P2P service: {}", e.getMessage());
      }
    }

    // Now safe to close connections (won't trigger ban)
    if (eventHandler != null) {
      try {
        eventHandler.closeAllConnections();
      } catch (Exception e) {
        log.warn("Error closing connections: {}", e.getMessage());
      }
    }
  }

  /**
   * EXTREME TPS MODE: Send messages continuously at maximum possible rate
   * This method runs in a tight loop without any sleep delays
   */
  private void extremeTpsSender() {
    // Silent start, no individual thread logs
    long messagesSent = 0;
    long errorCount = 0;

    try {
      while (!Thread.currentThread().isInterrupted()) {
        // Check if we can send
        if (!canPerformNetworkTest()) {
          // Wait a bit if no connections yet
          Thread.sleep(100);
          continue;
        }

        try {
          // Send in batches for efficiency
          for (int i = 0; i < 100; i++) {
            eventHandler.sendNetworkTestMessage(
                "extreme_tps",
                "EXTREME-" + Thread.currentThread().getId() + "-" + messagesSent,
                4  // Lower hop count for higher throughput
            );
            messagesSent++;
          }

          // No individual thread logs, unified by logTpsCounterStatistics

        } catch (Exception e) {
          errorCount++;
          // Don't spam logs with errors
          if (errorCount % 10000 == 1) {
            log.warn("Sender thread error (count: {}): {}", errorCount, e.getMessage());
          }
          // Brief pause on error to prevent tight error loop
          Thread.sleep(10);
        }
      }
    } catch (InterruptedException e) {
      // Silent exit, no individual thread logs
      Thread.currentThread().interrupt();
    }
  }

  private void logConfigurationSummary(P2pConfig config) {
    log.info("=== P2P Configuration Summary ===");
    log.info("Port: {}", config.getPort());
    log.info("Network ID: {}", config.getNetworkId());
    log.info("Discovery enabled: {}", config.isDiscoverEnable());
    log.info("Min connections: {}", config.getMinConnections());
    log.info("Max connections: {}", config.getMaxConnections());

    if (config.getSeedNodes() != null && !config.getSeedNodes().isEmpty()) {
      log.info("Seed nodes: {}", config.getSeedNodes());
    }

    if (config.getActiveNodes() != null && !config.getActiveNodes().isEmpty()) {
      log.info("Active nodes: {}", config.getActiveNodes());
    }

    if (config.getTreeUrls() != null && !config.getTreeUrls().isEmpty()) {
      log.info("Tree URLs: {}", config.getSeedNodes());
    }

    if (config.getPublishConfig() != null && config.getPublishConfig().isDnsPublishEnable()) {
      log.info("DNS publishing enabled for domain: {}", config.getPublishConfig().getDnsDomain());
    }

    log.info("================================");
  }
}
