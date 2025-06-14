package io.xdag.p2p.example;

import io.xdag.p2p.P2pService;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.dns.update.DnsType;
import io.xdag.p2p.example.config.ExampleConfig;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import io.xdag.p2p.example.message.TestMessage;
import io.xdag.p2p.stats.P2pStats;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * DNS-based P2P discovery example
 *
 * <p>Demonstrates two DNS usage patterns: 1. DNS Publishing - Publish your nodes to a DNS domain 2.
 * DNS Sync - Discover nodes from DNS tree URLs
 */
@Slf4j(topic = "dns-example")
public class DnsExample {

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private final DnsMode mode;

  public enum DnsMode {
    PUBLISH, // Publish nodes to DNS
    SYNC // Sync nodes from DNS
  }

  public DnsExample(DnsMode mode) {
    this.mode = mode;
  }

  /** Start DNS example with specified mode */
  public void start() {
    try {
      ExampleConfig config = createConfigForMode();

      // Initialize P2P service
      p2pService = new P2pService(config.toP2pConfig());

      // Create event handler
      eventHandler =
          new ExampleEventHandler() {
            @Override
            protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
              log.info("DNS peer connected: {}", channel.getInetSocketAddress());
              broadcastTestMessage("Hello from DNS example!");
            }

            @Override
            protected void onTestMessage(io.xdag.p2p.channel.Channel channel, TestMessage message) {
              log.info(
                  "DNS message from {}: {}",
                  channel.getInetSocketAddress(),
                  message.getContentAsString());
            }
          };

      p2pService.register(eventHandler);

      // Start the service
      log.info("Starting DNS example in {} mode...", mode);
      p2pService.start();
      log.info("DNS example started successfully");

      if (mode == DnsMode.PUBLISH) {
        logPublishInfo();
      }

      // Demonstrate usage
      demonstrateUsage();

    } catch (Exception e) {
      log.error("Failed to start DNS example: {}", e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  /** Create configuration based on mode */
  private ExampleConfig createConfigForMode() {
    switch (mode) {
      case PUBLISH:
        return createPublishConfig();
      case SYNC:
        return createSyncConfig();
      default:
        throw new IllegalArgumentException("Unknown DNS mode: " + mode);
    }
  }

  /** Create configuration for DNS publishing */
  private ExampleConfig createPublishConfig() {
    // Example configuration - replace with your actual values
    String dnsPrivate = "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291";
    String domain = "nodes.example.org";
    String accessKeyId = "your-access-key-id";
    String accessKeySecret = "your-access-key-secret";

    return ExampleConfig.dnsPublish(
        dnsPrivate,
        domain,
        DnsType.AwsRoute53, // or DnsType.AliYun
        accessKeyId,
        accessKeySecret);
  }

  /** Create configuration for DNS sync */
  private ExampleConfig createSyncConfig() {
    return ExampleConfig.dnsSync();
  }

  /** Demonstrate DNS usage */
  private void demonstrateUsage() throws InterruptedException {
    // Wait for initial connections
    Thread.sleep(10000);

    // Send test messages
    eventHandler.broadcastTestMessage("DNS discovery test message");

    // Log statistics
    logStatistics();

    if (mode == DnsMode.PUBLISH) {
      // For publish mode, wait longer to see DNS updates
      log.info("Waiting for DNS publishing... (this may take a few minutes)");
      Thread.sleep(300000); // 5 minutes
      logStatistics();
    }
  }

  /** Stop the DNS example */
  public void stop() {
    log.info("Stopping DNS example...");

    if (eventHandler != null) {
      eventHandler.closeAllConnections();
    }

    if (p2pService != null) {
      p2pService.close();
      log.info("DNS example stopped");
    }
  }

  /** Log publishing information */
  private void logPublishInfo() {
    if (p2pService != null && p2pService.getP2pConfig().getPublishConfig() != null) {
      var publishConfig = p2pService.getP2pConfig().getPublishConfig();
      log.info("=== DNS Publishing Configuration ===");
      log.info("Domain: {}", publishConfig.getDnsDomain());
      log.info("DNS Type: {}", publishConfig.getDnsType());
      log.info("Publishing enabled: {}", publishConfig.isDnsPublishEnable());
      log.info("After publishing, your tree URL will be:");
      log.info("tree://[PUBLIC_KEY]@{}", publishConfig.getDnsDomain());
      log.info("===================================");
    }
  }

  /** Log current statistics */
  private void logStatistics() {
    if (p2pService == null) return;

    log.info("=== DNS Example Statistics ===");
    log.info("Mode: {}", mode);
    log.info("Connected peers: {}", eventHandler.getChannels().size());
    log.info("Total nodes: {}", p2pService.getAllNodes().size());
    log.info("Table nodes: {}", p2pService.getTableNodes().size());
    log.info("Connectable nodes: {}", p2pService.getConnectableNodes().size());
    log.info("=============================");
  }

  /** Get P2P statistics */
  public P2pStats getStatistics() {
    return p2pService != null ? p2pService.getP2pStats() : null;
  }

  /** Get all nodes */
  public List<Node> getAllNodes() {
    return p2pService != null ? p2pService.getAllNodes() : List.of();
  }

  /** Connect to a specific peer */
  public void connectToPeer(InetSocketAddress address) {
    if (p2pService != null) {
      Node node = new Node(p2pService.getP2pConfig(), address);
      p2pService.connect(node, null);
      log.info("Attempting to connect to peer: {}", address);
    }
  }

  /** Main method for standalone execution */
  public static void main(String[] args) {
    // Determine mode from command line arguments
    DnsMode mode = DnsMode.SYNC; // Default to sync mode

    if (args.length > 0) {
      try {
        mode = DnsMode.valueOf(args[0].toUpperCase());
      } catch (IllegalArgumentException e) {
        log.error("Invalid mode: {}. Use PUBLISH or SYNC", args[0]);
        System.exit(1);
      }
    }

    log.info("Starting DNS example in {} mode", mode);

    DnsExample example = new DnsExample(mode);

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(example::stop));

    try {
      example.start();

      // Keep running
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(1000);
      }
    } catch (InterruptedException e) {
      log.info("DNS example interrupted");
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("DNS example failed: {}", e.getMessage(), e);
    } finally {
      example.stop();
    }
  }
}
