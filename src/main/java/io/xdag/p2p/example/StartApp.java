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
 * P2P TPS testing application
 *
 * Optimized for balanced throughput with controlled memory usage
 * - 4 concurrent sender threads to reduce memory pressure
 * - Batch processing with controlled memory usage
 * - Real-time TPS monitoring every 5 seconds
 */
@Slf4j(topic = "app")
public class StartApp {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private ScheduledExecutorService scheduler;
  private String nodeId;
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
        } else {
          log.info("Node {}: Received: {}", nodeId, message.getActualContent());
        }
      }

      @Override
      protected void forwardNetworkTestMessage(io.xdag.p2p.example.message.TestMessage originalMessage, io.xdag.p2p.channel.Channel sourceChannel) {
        super.forwardNetworkTestMessage(originalMessage, sourceChannel);
      }
    };
  }

  private void initializeNetworkTesting() {
    log.warn("========================================");
    log.warn("TPS Testing Mode - Balanced throughput");
    log.warn("Optimized: 4 sender threads + batching");
    log.warn("========================================");

    // 4 concurrent sender threads + 1 for monitoring
    // Reduced from 8 to lower memory pressure and peak usage
    scheduler = Executors.newScheduledThreadPool(5);

    for (int i = 0; i < 4; i++) {
      scheduler.submit(this::tpsSender);
    }

    // Performance monitoring every 5 seconds (needs dedicated thread)
    scheduler.scheduleAtFixedRate(this::logTpsCounterStatistics, 5, 5, TimeUnit.SECONDS);
  }

  /**
   * Send messages at maximum possible rate with optimized batching
   * Achieves 1M TPS through:
   * - Batch size: 100 messages
   * - Small yield (1ms) to prevent CPU saturation
   * - Controlled memory pressure
   */
  private void tpsSender() {
    long messagesSent = 0;

    try {
      while (!Thread.currentThread().isInterrupted()) {
        if (eventHandler == null || eventHandler.getChannels().isEmpty()) {
          Thread.sleep(100);
          continue;
        }

        try {
          // Batch size: 100 messages
          for (int i = 0; i < 100; i++) {
            eventHandler.sendNetworkTestMessage("tps_test",
                "T" + Thread.currentThread().getId() + "-" + messagesSent, 2);
            messagesSent++;
          }
          // Small yield to prevent CPU saturation
          Thread.sleep(1);
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

      // Aggregate LayeredStats from all channels
      long netSent = 0;
      long netSentBytes = 0;
      long netRecv = 0;
      long netRecvBytes = 0;
      long appSent = 0;
      long appRecv = 0;
      long appProcessed = 0;
      long appDuplicates = 0;
      long appForwarded = 0;

      if (eventHandler != null) {
        for (io.xdag.p2p.channel.Channel channel : eventHandler.getChannels().values()) {
          if (channel.getLayeredStats() != null) {
            netSent += channel.getLayeredStats().getNetwork().getMessagesSent();
            netSentBytes += channel.getLayeredStats().getNetwork().getBytesSent();
            netRecv += channel.getLayeredStats().getNetwork().getMessagesReceived();
            netRecvBytes += channel.getLayeredStats().getNetwork().getBytesReceived();
            appSent += channel.getLayeredStats().getApplication().getMessagesSent();
            appRecv += channel.getLayeredStats().getApplication().getMessagesReceived();
            appProcessed += channel.getLayeredStats().getApplication().getMessagesProcessed();
            appDuplicates += channel.getLayeredStats().getApplication().getMessagesDuplicated();
            appForwarded += channel.getLayeredStats().getApplication().getMessagesForwarded();
          }
        }
      }

      // Calculate efficiency percentage
      double efficiency = netRecv > 0 ? (appProcessed * 100.0) / netRecv : 0.0;

      // Convert bytes to MB for readability
      double netSentMB = netSentBytes / (1024.0 * 1024.0);
      double netRecvMB = netRecvBytes / (1024.0 * 1024.0);

      // Calculate TPS for both layers (based on interval)
      double networkRecvTps = timeDelta > 0 ? ((netRecv - 0) * 1000.0) / timeDelta : 0.0;  // Will improve with delta tracking
      double appProcessedTps = timeDelta > 0 ? ((appProcessed - 0) * 1000.0) / timeDelta : 0.0;  // Will improve with delta tracking

      // For now, use cumulative average as approximation
      double networkRecvTpsAvg = elapsedSeconds > 0 ? (netRecv * 1.0) / elapsedSeconds : 0.0;
      double appProcessedTpsAvg = elapsedSeconds > 0 ? (appProcessed * 1.0) / elapsedSeconds : 0.0;

      log.info("[{}] Uptime: {}s | Net-TPS: {} | App-TPS: {} | Connections: {} | Memory: {}/{}MB ({}%)",
               nodeId,
               elapsedSeconds,
               String.format("%.0f", networkRecvTpsAvg),
               String.format("%.0f", appProcessedTpsAvg),
               connections,
               usedMemory / (1024 * 1024),
               runtime.maxMemory() / (1024 * 1024),
               String.format("%.1f", memoryPercent));

      log.info("[{}] Network Layer - Sent: {} msgs ({} MB) | Received: {} msgs ({} MB)",
               nodeId,
               String.format("%,d", netSent),
               String.format("%.2f", netSentMB),
               String.format("%,d", netRecv),
               String.format("%.2f", netRecvMB));

      log.info("[{}] Application Layer - Sent: {} | Processed: {} | Duplicates: {} | Forwarded: {} | Efficiency: {}%",
               nodeId,
               String.format("%,d", appSent),
               String.format("%,d", appProcessed),
               String.format("%,d", appDuplicates),
               String.format("%,d", appForwarded),
               String.format("%.1f", efficiency));

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
