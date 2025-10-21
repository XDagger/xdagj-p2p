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

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.P2pService;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.example.cli.CliConfigParser;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Universal Node Discovery Testing Application
 *
 * <p>Supports both Kademlia DHT and EIP-1459 DNS-based node discovery.
 *
 * <h2>Modes:</h2>
 * <ul>
 *   <li><b>Kademlia DHT Mode</b>: Pure UDP-based discovery (default)</li>
 *   <li><b>DNS Discovery Mode</b>: EIP-1459 DNS-based discovery (when --url-schemes provided)</li>
 *   <li><b>Mock DNS Mode</b>: Local testing with in-memory DNS resolver (-Dmock.dns.enabled=true)</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>
 * # Kademlia DHT mode
 * java -cp xdagj-p2p.jar io.xdag.p2p.example.DiscoveryApp -p 10000 -a udp://host:port
 *
 * # DNS discovery mode (real DNS)
 * java -cp xdagj-p2p.jar io.xdag.p2p.example.DiscoveryApp -p 10000 \
 *   --url-schemes enrtree://PUBKEY@mainnet.nodes.xdag.io
 *
 * # DNS discovery mode (Mock DNS for testing)
 * java -Dmock.dns.enabled=true -cp xdagj-p2p.jar:target/test-classes \
 *   io.xdag.p2p.example.DiscoveryApp -p 10000 \
 *   --url-schemes enrtree://PUBKEY@mainnet.nodes.xdag.io
 * </pre>
 */
@Slf4j(topic = "discovery")
public class DiscoveryApp {

  private static final long DHT_MONITOR_INITIAL_DELAY_SECONDS = 10;
  private static final long DHT_MONITOR_PERIOD_SECONDS = 30;
  private static final long MAIN_LOOP_SLEEP_MILLIS = 5000;
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private P2pService p2pService;
  private ExampleEventHandler eventHandler;
  private ScheduledExecutorService scheduler;
  private String nodeId;
  private long startTime;
  private P2pConfig config;
  private boolean mockDnsEnabled = false;
  private boolean dnsMode = false;

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
    // Check if mock DNS is enabled via system property
    mockDnsEnabled = Boolean.parseBoolean(System.getProperty("mock.dns.enabled", "false"));

    if (mockDnsEnabled) {
      log.info("========================================");
      log.info("MOCK DNS MODE ENABLED");
      log.info("Using in-memory DNS resolver for testing");
      log.info("========================================");
      enableMockDns();
    }

    // Initialize configuration
    config = new P2pConfig();
    P2pConstant.version = 1;

    // Parse CLI arguments
    CliConfigParser parser = new CliConfigParser();
    boolean shouldStart = parser.parseAndConfigure(args, config);
    if (!shouldStart) {
      return;
    }

    // Detect DNS mode
    dnsMode = config.getTreeUrls() != null && !config.getTreeUrls().isEmpty();

    // Ensure discovery is enabled
    if (!config.isDiscoverEnable()) {
      log.warn("Discovery is disabled! Enabling it for discovery testing...");
      config.setDiscoverEnable(true);
    }

    logConfigurationSummary(config);

    // Setup mock DNS if enabled and in DNS mode
    if (mockDnsEnabled && dnsMode) {
      setupMockDnsRecords(config);
    }

    // Initialize P2P service
    p2pService = new P2pService(config);
    nodeId = (dnsMode ? "dns-node-" : "node-") + config.getPort();

    // Only create event handler in Kademlia mode
    if (!dnsMode) {
      eventHandler = createEventHandler();
      try {
        config.addP2pEventHandle(eventHandler);
      } catch (Exception e) {
        log.warn("Failed to register event handler: {}", e.getMessage());
      }
    }

    // Start service
    log.info("========================================");
    if (dnsMode) {
      log.info("Starting EIP-1459 DNS Discovery Test");
      log.info("========================================");
      log.info("Mode: DNS-based node discovery");
      log.info("DNS Tree URLs: {}", config.getTreeUrls());
      log.info("Mock DNS: {}", mockDnsEnabled ? "ENABLED" : "DISABLED");
    } else {
      log.info("Starting Kademlia DHT Discovery Test");
      log.info("========================================");
      log.info("Mode: UDP-based Kademlia DHT discovery");
      log.info("No performance testing messages will be sent");
    }
    log.info("========================================");

    startTime = System.currentTimeMillis();
    p2pService.start();
    log.info("P2P service started successfully");

    // Start monitoring
    initializeMonitoring();

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    runMainLoop();
  }

  /**
   * Enable mock DNS mode by loading the MockableLookUpTxt class
   */
  private void enableMockDns() {
    try {
      Class<?> mockableLookUpTxt = Class.forName("io.xdag.p2p.discover.dns.mock.MockableLookUpTxt");
      java.lang.reflect.Method setMockMode = mockableLookUpTxt.getMethod("setMockMode", boolean.class);
      setMockMode.invoke(null, true);
      log.info("Mock DNS mode activated successfully");
    } catch (Exception e) {
      log.error("Failed to enable mock DNS mode: {}", e.getMessage());
      log.error("Make sure mock DNS classes are in the test classpath");
      throw new RuntimeException("Mock DNS initialization failed", e);
    }
  }

  /**
   * Setup mock DNS records for testing
   */
  private void setupMockDnsRecords(P2pConfig config) {
    log.info("Setting up mock DNS records...");

    try {
      // Get MockDnsResolver instance via reflection
      Class<?> mockDnsResolver = Class.forName("io.xdag.p2p.discover.dns.mock.MockDnsResolver");
      java.lang.reflect.Method getInstance = mockDnsResolver.getMethod("getInstance");
      Object resolver = getInstance.invoke(null);
      java.lang.reflect.Method addRecord = mockDnsResolver.getMethod("addRecord", String.class, String.class);

      // Get the first tree URL
      String treeUrl = config.getTreeUrls().getFirst();
      log.info("Configuring mock DNS for tree URL: {}", treeUrl);

      // Parse tree URL: enrtree://PUBKEY@DOMAIN
      String[] parts = treeUrl.replace("enrtree://", "").split("@");
      if (parts.length != 2) {
        log.error("Invalid tree URL format: {}", treeUrl);
        return;
      }

      String pubkey = parts[0];
      String domain = parts[1];

      // Create sample mock DNS records
      // Root entry
      String rootEntry = String.format(
          "enrtree-root:v1 e=MOCKBRANCH001 l= seq=1 sig=0x%s",
          generateMockSignature()
      );
      addRecord.invoke(resolver, domain, rootEntry);
      log.info("Added root entry for domain: {}", domain);

      // Branch entry with mock nodes
      String branchEntry = "enrtree-branch:MOCKLEAF001,MOCKLEAF002";
      addRecord.invoke(resolver, "MOCKBRANCH001." + domain, branchEntry);
      log.info("Added branch entry");

      // Leaf entries with compressed node lists
      addMockNodesEntry(resolver, addRecord, "MOCKLEAF001." + domain, 3);
      addMockNodesEntry(resolver, addRecord, "MOCKLEAF002." + domain, 3);

      log.info("Mock DNS records setup complete");
      log.info("Total mock records created: 4 (1 root + 1 branch + 2 leaves)");

    } catch (Exception e) {
      log.error("Failed to setup mock DNS records: {}", e.getMessage(), e);
      throw new RuntimeException("Mock DNS setup failed", e);
    }
  }

  /**
   * Add a mock nodes entry with sample nodes
   */
  private void addMockNodesEntry(Object resolver, java.lang.reflect.Method addRecord,
                                 String domain, int nodeCount) throws Exception {
    List<DnsNode> dnsNodes = new ArrayList<>();

    for (int i = 0; i < nodeCount; i++) {
      // Generate a random node
      byte[] privateKeyBytes = new byte[32];
      SECURE_RANDOM.nextBytes(privateKeyBytes);
      ECKeyPair keyPair = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());

      String nodeId = keyPair.toAddress().toHexString();
      String ipv4 = "192.168.1." + (100 + i);
      String ipv6 = null;  // No IPv6 for simplicity
      int port = 10000 + i;

      DnsNode node = new DnsNode(nodeId, ipv4, ipv6, port);
      dnsNodes.add(node);
    }

    // Compress nodes to base64
    String compressed = DnsNode.compress(dnsNodes);
    String nodesEntry = "enr:" + compressed;

    addRecord.invoke(resolver, domain, nodesEntry);
    log.info("Added nodes entry: {} ({} nodes)", domain, nodeCount);
  }

  /**
   * Generate a mock signature (for testing only)
   */
  private String generateMockSignature() {
    byte[] sig = new byte[65];
    SECURE_RANDOM.nextBytes(sig);
    return Bytes.wrap(sig).toHexString().substring(2); // Remove 0x prefix
  }

  /**
   * Initialize monitoring to track node discovery progress
   */
  private void initializeMonitoring() {
    scheduler = Executors.newScheduledThreadPool(1);

    // Monitor at regular intervals
    scheduler.scheduleAtFixedRate(
        this::logStatistics,
        DHT_MONITOR_INITIAL_DELAY_SECONDS,
        DHT_MONITOR_PERIOD_SECONDS,
        TimeUnit.SECONDS);

    String mode = dnsMode ? "DNS" : "DHT";
    log.info("{} monitoring started (interval: {}s)", mode, DHT_MONITOR_PERIOD_SECONDS);
  }

  /**
   * Log discovery statistics (unified for both DHT and DNS modes)
   */
  private void logStatistics() {
    try {
      long currentTime = System.currentTimeMillis();
      long elapsedSeconds = (currentTime - startTime) / 1000;
      int tcpConnections = eventHandler != null ? eventHandler.getChannels().size() : 0;

      Runtime runtime = Runtime.getRuntime();
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      double memoryPercent = (usedMemory * 100.0) / runtime.maxMemory();

      log.info("========================================");
      if (dnsMode) {
        log.info("[{}] DNS Discovery Status at {}s", nodeId, elapsedSeconds);
        log.info("========================================");
        log.info("Mock DNS Mode: {}", mockDnsEnabled);
        log.info("DNS Tree URLs: {}", config.getTreeUrls());
      } else {
        log.info("[{}] Discovery Status at {}s", nodeId, elapsedSeconds);
        log.info("========================================");
        log.info("TCP Connections: {}", tcpConnections);
      }

      log.info("Memory: {}MB / {}MB ({}%)",
               usedMemory / (1024 * 1024),
               runtime.maxMemory() / (1024 * 1024),
               String.format("%.1f", memoryPercent));

      // Get discovered nodes statistics
      if (p2pService != null) {
        try {
          List<Node> connectableNodes = p2pService.getConnectableNodes();
          log.info("Discovered Nodes: {}", connectableNodes != null ? connectableNodes.size() : 0);

          if (connectableNodes != null && !connectableNodes.isEmpty()) {
            log.info("Sample {} Nodes:", dnsMode ? "Discovered" : "Connectable");
            int sampleSize = Math.min(5, connectableNodes.size());
            for (int i = 0; i < sampleSize; i++) {
              Node node = connectableNodes.get(i);
              String nodeIdStr = node.getId() != null ?
                  (node.getId().length() > 20 ? node.getId().substring(0, 20) + "..." : node.getId()) :
                  "null";
              log.info("  - Node ID: {}, Address: {}", nodeIdStr, node.getPreferInetSocketAddress());
            }
          }

        } catch (Exception e) {
          log.warn("Failed to get discovery statistics: {}", e.getMessage());
        }
      }

      log.info("========================================");
    } catch (Exception e) {
      log.error("Fatal error in monitoring task: {}", e.getMessage(), e);
    }
  }

  /**
   * Creates a minimal event handler that only logs connection events.
   * Does NOT send any test messages - completely silent handler.
   */
  private ExampleEventHandler createEventHandler() {
    return new ExampleEventHandler(nodeId) {
      @Override
      protected void onPeerConnected(io.xdag.p2p.channel.Channel channel) {
        log.info("[{}] Peer connected: {} (Total connections: {})",
                 nodeId, channel.getInetSocketAddress(), getChannels().size());
        // DO NOT send any messages - this is pure discovery mode
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

      @Override
      public void onConnect(io.xdag.p2p.channel.Channel channel) {
        // Override base class onConnect to prevent automatic message sending
        java.net.InetSocketAddress address = channel.getInetSocketAddress();
        getChannels().put(address, channel);
        log.info("[{}] TCP connection established: {}", nodeId, address);
        onPeerConnected(channel);
        // Note: Base class would call broadcastTestMessage here, but we don't!
      }
    };
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

    // Close connections (only in Kademlia mode)
    if (eventHandler != null) {
      try {
        eventHandler.closeAllConnections();
        eventHandler.shutdown();
      } catch (Exception e) {
        log.warn("Error closing connections: {}", e.getMessage());
      }
    }

    // Disable mock DNS if enabled
    if (mockDnsEnabled) {
      try {
        Class<?> mockableLookUpTxt = Class.forName("io.xdag.p2p.discover.dns.mock.MockableLookUpTxt");
        java.lang.reflect.Method setMockMode = mockableLookUpTxt.getMethod("setMockMode", boolean.class);
        setMockMode.invoke(null, false);

        Class<?> mockDnsResolver = Class.forName("io.xdag.p2p.discover.dns.mock.MockDnsResolver");
        java.lang.reflect.Method getInstance = mockDnsResolver.getMethod("getInstance");
        Object resolver = getInstance.invoke(null);
        java.lang.reflect.Method clear = mockDnsResolver.getMethod("clear");
        clear.invoke(resolver);

        log.info("Mock DNS cleared and disabled");
      } catch (Exception e) {
        log.warn("Error cleaning up mock DNS: {}", e.getMessage());
      }
    }

    log.info("Discovery application shutdown complete");
  }

  private void logConfigurationSummary(P2pConfig config) {
    log.info("=== Discovery Configuration ===");
    log.info("Mode: {}", dnsMode ? "DNS Discovery" : "Kademlia DHT");
    log.info("Port: {}", config.getPort());
    log.info("Network ID: {}", config.getNetworkId());
    log.info("Discovery enabled: {}", config.isDiscoverEnable());

    if (dnsMode) {
      log.info("DNS Tree URLs: {}", config.getTreeUrls());
    }

    if (config.getSeedNodes() != null && !config.getSeedNodes().isEmpty()) {
      log.info("TCP Seed nodes: {}", config.getSeedNodes());
    }

    if (config.getActiveNodes() != null && !config.getActiveNodes().isEmpty()) {
      log.info("UDP Active nodes: {}", config.getActiveNodes());
    }

    if (!dnsMode) {
      log.info("Min connections: {}", config.getMinConnections());
      log.info("Max connections: {}", config.getMaxConnections());
    }

    log.info("================================");
  }
}
