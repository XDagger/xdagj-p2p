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

      // registration removed in refactor

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
    return switch (mode) {
      case PUBLISH -> createPublishConfig();
      case SYNC -> createSyncConfig();
      default -> throw new IllegalArgumentException("Unknown DNS mode: " + mode);
    };
  }

  /** Create configuration for DNS publishing */
  private ExampleConfig createPublishConfig() {
    // Load credentials from environment variables for security
    String dnsPrivate = System.getenv("DNS_PRIVATE_KEY");
    String domain = System.getenv("DNS_DOMAIN");
    String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
    String accessKeySecret = System.getenv("AWS_SECRET_ACCESS_KEY");

    // Validate required environment variables
    if (dnsPrivate == null || domain == null || accessKeyId == null || accessKeySecret == null) {
      log.error("Missing required environment variables for DNS publishing.");
      log.error("Please set the following environment variables:");
      log.error("  DNS_PRIVATE_KEY     - Your DNS signing private key (hex format)");
      log.error("  DNS_DOMAIN          - Your DNS domain (e.g., nodes.xdag.org)");
      log.error("  AWS_ACCESS_KEY_ID   - AWS IAM access key");
      log.error("  AWS_SECRET_ACCESS_KEY - AWS IAM secret key");
      log.error("");
      log.error("Example:");
      log.error("  export DNS_PRIVATE_KEY=\"your-private-key-hex\"");
      log.error("  export DNS_DOMAIN=\"nodes.example.org\"");
      log.error("  export AWS_ACCESS_KEY_ID=\"AKIAIOSFODNN7EXAMPLE\"");
      log.error("  export AWS_SECRET_ACCESS_KEY=\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"");
      log.error("");
      log.error("See docs/DNS_CONFIGURATION.md for detailed setup instructions.");
      throw new IllegalStateException("DNS publishing configuration not found");
    }

    return ExampleConfig.dnsPublish(
        dnsPrivate,
        domain,
        DnsType.AwsRoute53,
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
      p2pService.stop();
      log.info("DNS example stopped");
    }
  }

  /** Log publishing information */
  private void logPublishInfo() {
    if (p2pService != null && configForLog() != null) {
      var publishConfig = configForLog();
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
    log.info("Connectable nodes: {}", p2pService.getConnectableNodes().size());
    log.info("=============================");
  }

  /** Get P2P statistics */
  public P2pStats getStatistics() {
    return p2pService != null ? p2pService.getP2pStats() : null;
  }

  /** Get all nodes */
  public List<Node> getAllNodes() {
    return List.of();
  }

  /** Connect to a specific peer */
  public void connectToPeer(InetSocketAddress address) {
    if (p2pService != null) {
      p2pService.connect(address);
      log.info("Attempting to connect to peer: {}", address);
    }
  }

  private io.xdag.p2p.discover.dns.update.PublishConfig configForLog() {
    // This example doesn't expose direct config anymore; return null by default
    return null;
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
