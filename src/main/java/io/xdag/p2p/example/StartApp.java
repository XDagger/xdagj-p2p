package io.xdag.p2p.example;

import io.xdag.p2p.P2pService;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.example.cli.CliConfigParser;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Main application for starting P2P service with CLI configuration
 *
 * <p>This is a simplified and elegant version that uses: - CliConfigParser for command line
 * argument handling - ExampleEventHandler for P2P event handling - Proper error handling and
 * logging
 */
@Slf4j(topic = "app")
public class StartApp {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;

  public static void main(String[] args) {
    StartApp app = new StartApp();
    try {
      app.start(args);
    } catch (Exception e) {
      log.error("Failed to start P2P application: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  /**
   * Start the P2P application with given command line arguments
   *
   * @param args command line arguments
   */
  public void start(String[] args) throws Exception {
    // Initialize configuration
    P2pConfig config = new P2pConfig();
    P2pConstant.version = 1;

    // Parse command line arguments
    CliConfigParser parser = new CliConfigParser();
    boolean shouldStart = parser.parseAndConfigure(args, config);

    if (!shouldStart) {
      // Help was printed, exit gracefully
      return;
    }

    // Log configuration summary
    logConfigurationSummary(config);

    // Initialize P2P service
    p2pService = new P2pService(config);

    // Register event handler
    eventHandler =
        new ExampleEventHandler() {
          @Override
          protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
            log.info("New peer connected: {}", channel.getInetSocketAddress());
            // Send welcome message to new peer
            broadcastTestMessage("Welcome to the P2P network!");
          }
        };

    try {
      p2pService.register(eventHandler);
    } catch (Exception e) {
      throw new RuntimeException("Failed to register event handler", e);
    }

    // Start the service
    log.info("Starting P2P service...");
    p2pService.start();
    log.info("P2P service started successfully");

    // Add shutdown hook for graceful shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    // Keep the application running
    runMainLoop();
  }

  /** Main application loop */
  private void runMainLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(1000);

        // Optionally log statistics periodically
        if (System.currentTimeMillis() % 30000 < 1000) { // Every 30 seconds
          logStatistics();
        }
      }
    } catch (InterruptedException e) {
      log.info("Application interrupted, shutting down...");
      Thread.currentThread().interrupt();
    }
  }

  /** Graceful shutdown */
  private void shutdown() {
    log.info("Shutting down P2P application...");

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

  /** Log configuration summary */
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

  /** Log current statistics */
  private void logStatistics() {
    if (p2pService != null) {
      var stats = p2pService.getP2pStats();
      log.info("=== P2P Statistics ===");
      log.info("Connected peers: {}", eventHandler.getChannels().size());
      log.info("Total nodes: {}", p2pService.getAllNodes().size());
      log.info("Table nodes: {}", p2pService.getTableNodes().size());
      log.info("Connectable nodes: {}", p2pService.getConnectableNodes().size());
      log.info("======================");
    }
  }
}
