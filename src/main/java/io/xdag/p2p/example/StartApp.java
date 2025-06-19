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

    logConfigurationSummary(config);

    // Initialize P2P service and event handler
    p2pService = new P2pService(config);
    nodeId = "node-" + config.getPort();
    eventHandler = createEventHandler();

    p2pService.register(eventHandler);

    // Start the service
    log.info("Starting P2P service...");
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
        log.info("New peer connected: {}", channel.getInetSocketAddress());
        broadcastTestMessage("Welcome to the P2P network from " + nodeId + "!");
        
        // Start testing when we have multiple connections
        if (getChannels().size() >= 2) {
          log.info("Starting network performance tests with {} connections", getChannels().size());
          sendNetworkTestMessage("connection_test", "Testing new connection from " + nodeId, 6);
        }
      }

      @Override
      protected void onTestMessage(io.xdag.p2p.channel.Channel channel, io.xdag.p2p.example.message.TestMessage message) {
        if (message.isNetworkTestMessage()) {
          log.info("Node {}: Received network test message: {} (hops: {}, latency: {}ms, type: {})", 
                  nodeId, message.getMessageId(), message.getHopCount(), message.getAge(), message.getTestType());
        } else {
          log.info("Node {}: Received regular message from {}: {}", 
                  nodeId, channel.getInetSocketAddress(), message.getActualContent());
        }
      }
    };
  }

  private void initializeNetworkTesting() {
    scheduler = Executors.newScheduledThreadPool(6); // Increased thread pool size
    
    // Schedule intensive network tests for professional stress testing
    scheduler.scheduleAtFixedRate(this::performNetworkTests, 10, 3, TimeUnit.SECONDS); // Increased frequency
    scheduler.scheduleAtFixedRate(this::performBurstTests, 30, 12, TimeUnit.SECONDS);  // Increased frequency
    scheduler.scheduleAtFixedRate(this::performStabilityTests, 60, 25, TimeUnit.SECONDS); // Increased frequency
    scheduler.scheduleAtFixedRate(this::performNetworkAnalysisTests, 90, 45, TimeUnit.SECONDS); // New analysis tests
    scheduler.scheduleAtFixedRate(this::logNetworkTestStatistics, 20, 8, TimeUnit.SECONDS); // More frequent logging
    scheduler.scheduleAtFixedRate(this::exportDetailedStatistics, 300, 300, TimeUnit.SECONDS); // Export every 5 minutes
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
    return eventHandler != null && eventHandler.getChannels().size() > 0;
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

  private void logNetworkTestStatistics() {
    if (eventHandler != null) {
      try {
        String stats = eventHandler.getNetworkTestStatistics();
        log.info("Network test statistics: {}", stats);
      } catch (Exception e) {
        log.warn("Error logging network test statistics: {}", e.getMessage());
      }
    }
  }

  /**
   * Export detailed network statistics to file for professional analysis
   */
  private void exportDetailedStatistics() {
    if (eventHandler != null) {
      try {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String detailedStats = String.format(
            "{\"timestamp\": \"%s\", \"nodeId\": \"%s\", \"connections\": %d, \"stats\": \"%s\"}",
            timestamp, nodeId, eventHandler.getChannels().size(), 
            eventHandler.getNetworkTestStatistics().replace("\"", "\\\"")
        );
        
        // Log to console for now - could be enhanced to write to file
        log.info("DETAILED_STATS: {}", detailedStats);
      } catch (Exception e) {
        log.warn("Error exporting detailed statistics: {}", e.getMessage());
      }
    }
  }

  private void shutdown() {
    log.info("Shutting down P2P application...");

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

    if (eventHandler != null) {
      try {
        eventHandler.closeAllConnections();
      } catch (Exception e) {
        log.warn("Error closing connections: {}", e.getMessage());
      }
    }

    if (p2pService != null) {
      try {
        p2pService.close();
        log.info("P2P service stopped");
      } catch (Exception e) {
        log.error("Error stopping P2P service: {}", e.getMessage());
      }
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
      log.info("Tree URLs: {}", config.getTreeUrls());
    }

    if (config.getPublishConfig() != null && config.getPublishConfig().isDnsPublishEnable()) {
      log.info("DNS publishing enabled for domain: {}", config.getPublishConfig().getDnsDomain());
    }

    log.info("================================");
  }
}
