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
 * Simplified P2P TPS testing application
 *
 * Goal: Achieve 100K TPS
 *
 * Two modes:
 * - NORMAL: Moderate testing (~1K-5K TPS)
 * - EXTREME (EXTREME_TPS_MODE=true): Maximum TPS testing (target 100K TPS)
 */
@Slf4j(topic = "app")
public class StartApp {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private ScheduledExecutorService scheduler;
  private String nodeId;
  private boolean enableDetailedLogging;
  private long startTime;

  // TPS measurement
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

    // Parse CLI arguments
    CliConfigParser parser = new CliConfigParser();
    boolean shouldStart = parser.parseAndConfigure(args, config);
    if (!shouldStart) {
      return;
    }

    // Check mode
    enableDetailedLogging = !"false".equalsIgnoreCase(System.getenv("ENABLE_DETAILED_LOGGING"));
    log.info("Detailed logging: {}", enableDetailedLogging ? "ENABLED" : "DISABLED (Maximum TPS mode)");

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
    log.info("Starting P2P service...");
    startTime = System.currentTimeMillis();
    p2pService.start();
    log.info("P2P service started successfully");

    // Start testing
    initializeNetworkTesting();

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    runMainLoop();
  }

  private ExampleEventHandler createEventHandler() {
    return new ExampleEventHandler(nodeId) {
      @Override
      protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
        log.info("[{}] Connected: {} (Total: {})",
                 nodeId, channel.getInetSocketAddress(), getChannels().size());
        broadcastTestMessage("Welcome from " + nodeId);
      }

      @Override
      protected void onPeerDisconnected(io.xdag.p2p.channel.Channel channel) {
        log.info("[{}] Disconnected: {} (Remaining: {})",
                 nodeId, channel.getInetSocketAddress(), getChannels().size());
      }

      @Override
      protected void onTestMessage(io.xdag.p2p.channel.Channel channel, io.xdag.p2p.example.message.TestMessage message) {
        if (message.isNetworkTestMessage()) {
          messageCounter.incrementAndGet();

          if (enableDetailedLogging) {
            log.info("MSG_RECEIVED|{}|{}|{}|{}",
                    System.currentTimeMillis(), nodeId, message.getMessageId(), message.getOriginSender());
          }
        } else {
          log.info("Node {}: Received: {}", nodeId, message.getActualContent());
        }
      }

      @Override
      protected void forwardNetworkTestMessage(io.xdag.p2p.example.message.TestMessage originalMessage) {
        super.forwardNetworkTestMessage(originalMessage);
        if (enableDetailedLogging && !originalMessage.isExpired()) {
          log.info("MSG_FORWARDED|{}|{}|{}|{}",
                  System.currentTimeMillis(), nodeId, originalMessage.getMessageId(),
                  originalMessage.getOriginSender());
        }
      }
    };
  }

  private void initializeNetworkTesting() {
    boolean extremeTpsMode = "true".equalsIgnoreCase(System.getenv("EXTREME_TPS_MODE"));

    if (extremeTpsMode) {
      log.warn("======================================");
      log.warn("EXTREME TPS MODE - TARGET: 100K TPS");
      log.warn("Maximum message load");
      log.warn("======================================");

      // 32 concurrent sender threads
      scheduler = Executors.newScheduledThreadPool(32);

      for (int i = 0; i < 32; i++) {
        scheduler.submit(this::extremeTpsSender);
      }

      // Performance monitoring every 5 seconds
      scheduler.scheduleAtFixedRate(this::logTpsCounterStatistics, 5, 5, TimeUnit.SECONDS);

    } else {
      // Normal mode: moderate testing
      scheduler = Executors.newScheduledThreadPool(4);

      // Send messages at moderate rate: 100 Hz × 10 msg = 1K msg/s per node
      scheduler.scheduleAtFixedRate(this::sendTpsTestMessages, 1, 10, TimeUnit.MILLISECONDS);

      // Performance monitoring
      if (!enableDetailedLogging) {
        scheduler.scheduleAtFixedRate(this::logTpsCounterStatistics, 5, 5, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Normal mode: Send moderate TPS test messages
   */
  private void sendTpsTestMessages() {
    if (eventHandler == null || eventHandler.getChannels().isEmpty()) {
      return;
    }

    try {
      // Send 10 messages per execution
      for (int i = 0; i < 10; i++) {
        eventHandler.sendNetworkTestMessage("tps_test",
            "TPS-" + System.nanoTime(), 4);
      }
    } catch (Exception e) {
      // Silent failure
    }
  }

  /**
   * EXTREME MODE: Send messages at maximum possible rate
   * Target: 100K TPS
   */
  private void extremeTpsSender() {
    long messagesSent = 0;

    try {
      while (!Thread.currentThread().isInterrupted()) {
        if (eventHandler == null || eventHandler.getChannels().isEmpty()) {
          Thread.sleep(100);
          continue;
        }

        try {
          // Send in large batches for maximum efficiency
          for (int i = 0; i < 200; i++) {
            eventHandler.sendNetworkTestMessage("tps_test",
                "EXT-" + Thread.currentThread().getId() + "-" + messagesSent, 3);
            messagesSent++;
          }
        } catch (Exception e) {
          // Brief pause on error
          Thread.sleep(10);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

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

      Runtime runtime = Runtime.getRuntime();
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      double memoryPercent = (usedMemory * 100.0) / runtime.maxMemory();

      log.info("[{}] Uptime: {}s | TPS: {} | Messages: {} | Connections: {} | Memory: {}/{}MB ({}%)",
               nodeId,
               elapsedSeconds,
               String.format("%.0f", intervalTps),
               String.format("%,d", currentCount),
               connections,
               usedMemory / (1024 * 1024),
               runtime.maxMemory() / (1024 * 1024),
               String.format("%.1f", memoryPercent));

      lastCounterSnapshot.set(currentCount);
      lastCounterTime.set(currentTime);
    }
  }

  private void runMainLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(5000);
      }
    } catch (InterruptedException e) {
      log.info("Application interrupted, shutting down...");
      Thread.currentThread().interrupt();
    }
  }

  private void shutdown() {
    log.info("Shutting down P2P application...");

    // Stop scheduler
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
      } catch (Exception e) {
        log.warn("Error closing connections: {}", e.getMessage());
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

    log.info("================================");
  }
}
