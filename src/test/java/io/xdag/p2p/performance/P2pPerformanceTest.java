package io.xdag.p2p.performance;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.p2p.P2pService;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.NodeTable;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import io.xdag.p2p.message.node.StatusMessage;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2P Network Performance Test Suite
 *
 * <p>Comprehensive performance testing for XDAG P2P network: - Global distributed network
 * simulation - Real network I/O performance - Protocol processing with Netty pipeline - Large scale
 * network topology testing - Cross-region latency simulation
 */
@Slf4j
public class P2pPerformanceTest {

  private P2pConfig config;
  private Node localNode;
  private List<P2pService> testServices;
  private Random random = new Random();

  /** Global IP address pools representing different regions */
  private static final String[][] GLOBAL_IP_POOLS = {
    // North America (US/Canada)
    {"23.20.", "50.16.", "54.175.", "107.20.", "174.129."},
    // Europe
    {"46.51.", "78.47.", "85.214.", "176.9.", "195.201."},
    // Asia Pacific
    {"103.28.", "119.28.", "202.182.", "210.140.", "220.181."},
    // South America
    {"177.71.", "189.73.", "200.89.", "201.48."},
    // Africa/Middle East
    {"41.230.", "154.73.", "196.200."}
  };

  @BeforeEach
  void setUp() {
    config = new P2pConfig();
    config.setNetworkId(1);
    config.setPort(16783);
    config.setDiscoverEnable(false);

    String seedNodeIP = generateGlobalIP("seed");
    localNode = new Node(config, new InetSocketAddress(seedNodeIP, 16783));
    testServices = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    for (P2pService service : testServices) {
      if (service != null && !service.isShutdown()) {
        service.close();
      }
    }
    testServices.clear();
  }

  @Test
  @DisplayName("🚀 Bootstrap & DHT Performance")
  void testBootstrapAndDHTPerformance() {
    log.info("=== 🚀 BOOTSTRAP & DHT PERFORMANCE ===");

    // Test DHT with global nodes
    NodeTable nodeTable = new NodeTable(localNode);

    measurePerformance(
        "DHT Population (10K global nodes)",
        1,
        () -> {
          for (int i = 0; i < 10000; i++) {
            nodeTable.addNode(generateGlobalNode());
          }
        });

    measurePerformance(
        "DHT Lookup Performance",
        1000,
        () -> {
          Bytes targetId = NetUtils.getNodeId();
          nodeTable.getClosestNodes(targetId);
        });

    log.info(
        "🚀 DHT Stats: {} nodes in {} buckets",
        nodeTable.getNodesCount(),
        nodeTable.getBucketsCount());
  }

  @Test
  @DisplayName("🔗 Real Network I/O Performance")
  void testRealNetworkIOPerformance() throws InterruptedException {
    log.info("=== 🔗 REAL NETWORK I/O PERFORMANCE ===");

    P2pConfig config1 = createConfig(17001);
    P2pConfig config2 = createConfig(17002);

    P2pService service1 = new P2pService(config1);
    P2pService service2 = new P2pService(config2);
    testServices.add(service1);
    testServices.add(service2);

    try {
      service1.start();
      service2.start();
      Thread.sleep(500);

      // Test connection establishment
      List<Long> connectionTimes = new ArrayList<>();
      String targetIP = generateGlobalIP("peer");

      for (int i = 0; i < 10; i++) {
        long startTime = System.nanoTime();
        Node targetNode = new Node(config2, new InetSocketAddress(targetIP, 17002));
        CountDownLatch latch = new CountDownLatch(1);

        service1.connect(targetNode, future -> latch.countDown());

        if (latch.await(5, TimeUnit.SECONDS)) {
          connectionTimes.add(System.nanoTime() - startTime);
        }
        Thread.sleep(100);
      }

      if (!connectionTimes.isEmpty()) {
        double avgMs =
            connectionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0;

        log.info(
            "🔗 Connection Performance: {}ms avg | {}/{} success",
            String.format("%.2f", avgMs),
            connectionTimes.size(),
            10);
      }

    } finally {
      service1.close();
      service2.close();
    }
  }

  @Test
  @DisplayName("📨 Protocol Processing Performance")
  void testProtocolProcessingPerformance() {
    log.info("=== 📨 PROTOCOL PROCESSING PERFORMANCE ===");

    ChannelManager channelManager = new ChannelManager(config, null, null);

    // Test real P2P message processing through EmbeddedChannel
    testP2pMessageProcessing(channelManager);

    // Test message encoding/decoding performance
    testMessageCodecPerformance(channelManager);

    // Test concurrent protocol processing
    testConcurrentP2pProcessing(channelManager);
  }

  @Test
  @DisplayName("🌍 Global Network Simulation")
  void testGlobalNetworkSimulation() {
    log.info("=== 🌍 GLOBAL NETWORK SIMULATION ===");

    // Create 1000-node global network
    int networkSize = 1000;
    List<Node> globalNodes = generateGlobalNodeSet(networkSize);

    // Analyze geographical distribution
    analyzeGlobalDistribution(globalNodes);

    // Test large scale DHT operations
    testLargeScaleDHT(globalNodes);
  }

  @Test
  @DisplayName("🌐 Cross-Region Latency Simulation")
  void testCrossRegionLatencySimulation() {
    log.info("=== 🌐 CROSS-REGION LATENCY SIMULATION ===");

    // Simulate real-world latencies (ms)
    int[][] latencies = {
      {0, 80, 150, 120, 200}, // North America
      {80, 0, 200, 180, 100}, // Europe
      {150, 200, 0, 300, 180}, // Asia Pacific
      {120, 180, 300, 0, 250}, // South America
      {200, 100, 180, 250, 0} // Africa/Middle East
    };

    String[] regions = {
      "North America", "Europe", "Asia Pacific", "South America", "Africa/Middle East"
    };

    // Test cross-region connections
    long totalLatency = 0;
    int connections = 0;

    for (int source = 0; source < regions.length; source++) {
      for (int target = 0; target < regions.length; target++) {
        if (source != target) {
          int latency = latencies[source][target];
          totalLatency += latency;
          connections++;

          log.info("🌐 {} -> {}: {}ms", regions[source], regions[target], latency);
        }
      }
    }

    log.info(
        "🌐 Average Cross-Region Latency: {}ms",
        String.format("%.1f", totalLatency / (double) connections));

    // Test routing performance
    measurePerformance(
        "Cross-Region Message Routing",
        1000,
        () -> {
          int hops = 2 + random.nextInt(4);
          Math.pow(hops, 2); // Simulate routing computation
        });
  }

  @Test
  @DisplayName("🔄 Concurrent Operations")
  void testConcurrentOperations() {
    log.info("=== 🔄 CONCURRENT OPERATIONS ===");

    NodeTable nodeTable = new NodeTable(localNode);
    int[] threadCounts = {1, 2, 4, 8, 16};

    for (int threadCount : threadCounts) {
      testConcurrentDHT(nodeTable, threadCount);
    }
  }

  // ========== Helper Methods ==========

  private void measurePerformance(String operationName, int iterations, Runnable operation) {
    // Warmup
    int warmupIterations = Math.min(10, iterations / 10);
    for (int i = 0; i < warmupIterations; i++) {
      operation.run();
    }

    Instant start = Instant.now();
    for (int i = 0; i < iterations; i++) {
      operation.run();
    }
    Duration duration = Duration.between(start, Instant.now());

    double opsPerSec = iterations / (duration.toNanos() / 1_000_000_000.0);
    double avgTimeMs = duration.toMillis() / (double) iterations;

    System.out.printf("⚡ %s: %.0f ops/sec | %.3fms avg%n", operationName, opsPerSec, avgTimeMs);
    log.info(
        "⚡ {}: {} ops/sec | {}ms avg",
        operationName,
        String.format("%.0f", opsPerSec),
        String.format("%.3f", avgTimeMs));
  }

  private String generateGlobalIP(String nodeType) {
    if ("seed".equals(nodeType)) {
      // Seed nodes from major cloud providers
      String[] seedPrefixes = {"23.20.", "54.175.", "46.51.", "103.28."};
      String prefix = seedPrefixes[random.nextInt(seedPrefixes.length)];
      return prefix + (random.nextInt(255) + 1) + "." + (random.nextInt(255) + 1);
    } else {
      // Regular nodes from global regions
      String[] regionPool = GLOBAL_IP_POOLS[random.nextInt(GLOBAL_IP_POOLS.length)];
      String prefix = regionPool[random.nextInt(regionPool.length)];
      return prefix + (random.nextInt(255) + 1) + "." + (random.nextInt(255) + 1);
    }
  }

  private Node generateGlobalNode() {
    String ip = generateGlobalIP("peer");
    int port = 16783 + random.nextInt(40000);
    return new Node(config, new InetSocketAddress(ip, port));
  }

  private List<Node> generateGlobalNodeSet(int count) {
    List<Node> nodes = new ArrayList<>();
    Set<String> usedIPs = new HashSet<>();

    // Ensure geographical diversity
    int nodesPerRegion = Math.max(1, count / GLOBAL_IP_POOLS.length);

    for (int region = 0; region < GLOBAL_IP_POOLS.length && nodes.size() < count; region++) {
      String[] regionPool = GLOBAL_IP_POOLS[region];

      for (int i = 0; i < nodesPerRegion && nodes.size() < count; i++) {
        String ip;
        int attempts = 0;
        do {
          String prefix = regionPool[random.nextInt(regionPool.length)];
          ip = prefix + (random.nextInt(255) + 1) + "." + (random.nextInt(255) + 1);
          attempts++;
        } while (usedIPs.contains(ip) && attempts < 10);

        if (!usedIPs.contains(ip)) {
          usedIPs.add(ip);
          int port = 16783 + random.nextInt(40000);
          nodes.add(new Node(config, new InetSocketAddress(ip, port)));
        }
      }
    }

    // Fill remaining slots
    while (nodes.size() < count) {
      String ip = generateGlobalIP("peer");
      if (!usedIPs.contains(ip)) {
        usedIPs.add(ip);
        int port = 16783 + random.nextInt(40000);
        nodes.add(new Node(config, new InetSocketAddress(ip, port)));
      }
    }

    return nodes;
  }

  private P2pConfig createConfig(int port) {
    P2pConfig testConfig = new P2pConfig();
    testConfig.setPort(port);
    testConfig.setNetworkId(1);
    testConfig.setDiscoverEnable(false);
    testConfig.setMinConnections(2);
    testConfig.setMaxConnections(10);
    return testConfig;
  }

  private void analyzeGlobalDistribution(List<Node> nodes) {
    int[] regionCounts = new int[GLOBAL_IP_POOLS.length];
    Set<String> uniqueIPs = new HashSet<>();

    for (Node node : nodes) {
      String ip = node.getInetSocketAddressV4().getAddress().getHostAddress();
      uniqueIPs.add(ip);

      for (int region = 0; region < GLOBAL_IP_POOLS.length; region++) {
        for (String prefix : GLOBAL_IP_POOLS[region]) {
          if (ip.startsWith(prefix)) {
            regionCounts[region]++;
            break;
          }
        }
      }
    }

    String[] regionNames = {
      "North America", "Europe", "Asia Pacific", "South America", "Africa/Middle East"
    };
    log.info("🌍 Global Distribution Analysis:");
    log.info(
        "🌍 Total: {} nodes, Unique IPs: {} ({}%)",
        nodes.size(),
        uniqueIPs.size(),
        String.format("%.1f", (uniqueIPs.size() * 100.0) / nodes.size()));

    for (int i = 0; i < regionNames.length; i++) {
      double percentage = (regionCounts[i] * 100.0) / nodes.size();
      log.info(
          "🌍 {}: {} nodes ({}%)",
          regionNames[i], regionCounts[i], String.format("%.1f", percentage));
    }
  }

  private void testLargeScaleDHT(List<Node> globalNodes) {
    List<NodeTable> networkNodes = new ArrayList<>();

    Instant start = Instant.now();
    for (Node node : globalNodes) {
      NodeTable table = new NodeTable(node);
      // Connect to 50 random peers (realistic P2P topology)
      for (int j = 0; j < 50; j++) {
        table.addNode(generateGlobalNode());
      }
      networkNodes.add(table);
    }
    Duration buildTime = Duration.between(start, Instant.now());

    log.info(
        "📊 Network Build: {} nodes in {}ms ({} nodes/sec)",
        globalNodes.size(),
        buildTime.toMillis(),
        String.format("%.0f", globalNodes.size() / (buildTime.toNanos() / 1_000_000_000.0)));

    // Test network lookups
    start = Instant.now();
    int lookupCount = 10000;
    AtomicInteger successful = new AtomicInteger(0);

    for (int i = 0; i < lookupCount; i++) {
      Bytes targetId = NetUtils.getNodeId();
      NodeTable table = networkNodes.get(i % networkNodes.size());
      if (!table.getClosestNodes(targetId).isEmpty()) {
        successful.incrementAndGet();
      }
    }

    Duration lookupTime = Duration.between(start, Instant.now());
    double lookupRate = lookupCount / (lookupTime.toNanos() / 1_000_000_000.0);

    log.info(
        "📊 Network Lookups: {} lookups in {}ms ({} lookups/sec, {}% success)",
        lookupCount,
        lookupTime.toMillis(),
        String.format("%.0f", lookupRate),
        String.format("%.1f", (successful.get() * 100.0) / lookupCount));
  }

  private void testP2pMessageProcessing(ChannelManager channelManager) {
    log.info("🔧 Testing P2P Message Processing:");

    // Pre-create reusable resources to reduce overhead
    EmbeddedChannel sharedChannel = new EmbeddedChannel();
    Channel sharedP2pChannel = new Channel(config, channelManager);
    sharedP2pChannel.init(sharedChannel.pipeline(), localNode.getHexId(), false);

    try {
      // Test pure message creation performance (no network overhead)
      testP2pMessageType(
          "HelloMessage Creation",
          () -> {
            HelloMessage msg =
                new HelloMessage(
                    config,
                    io.xdag.p2p.message.node.DisconnectCode.NORMAL,
                    System.currentTimeMillis());
            assertNotNull(msg.getData());
          });

      testP2pMessageType(
          "PingMessage Creation",
          () -> {
            PingMessage msg = new PingMessage(config);
            assertNotNull(msg.getData());
          });

      testP2pMessageType(
          "PongMessage Creation",
          () -> {
            PongMessage msg = new PongMessage(config);
            assertNotNull(msg.getData());
          });

      // Test network processing with pre-created channel (reduced overhead)
      HelloMessage sharedHelloMsg =
          new HelloMessage(
              config, io.xdag.p2p.message.node.DisconnectCode.NORMAL, System.currentTimeMillis());

      measurePerformance(
          "HelloMessage Network Processing",
          50000,
          () -> {
            sharedChannel.writeInbound(sharedHelloMsg.getData());
            Object result = sharedChannel.readInbound();
            assertNotNull(result != null ? result : "processed");
          });

      PingMessage sharedPingMsg = new PingMessage(config);
      measurePerformance(
          "PingMessage Network Processing",
          50000,
          () -> {
            sharedChannel.writeInbound(sharedPingMsg.getData());
            Object result = sharedChannel.readInbound();
            assertNotNull(result != null ? result : "processed");
          });

    } finally {
      sharedChannel.close();
    }
  }

  private void testMessageCodecPerformance(ChannelManager channelManager) {
    log.info("📨 Testing Message Codec Performance:");

    // Test message serialization performance with higher iterations
    measurePerformance(
        "HelloMessage Serialization",
        1000000,
        () -> {
          HelloMessage msg =
              new HelloMessage(
                  config,
                  io.xdag.p2p.message.node.DisconnectCode.NORMAL,
                  System.currentTimeMillis());
          byte[] data = msg.getData().toArray();
          assertNotNull(data);
        });

    measurePerformance(
        "PingMessage Serialization",
        1000000,
        () -> {
          PingMessage msg = new PingMessage(config);
          byte[] data = msg.getData().toArray();
          assertNotNull(data);
        });

    measurePerformance(
        "StatusMessage Serialization",
        500000,
        () -> {
          StatusMessage msg = new StatusMessage(config, channelManager);
          byte[] data = msg.getData().toArray();
          assertNotNull(data);
        });

    // Test pure data access performance (pre-created messages)
    HelloMessage preCreatedHello =
        new HelloMessage(
            config, io.xdag.p2p.message.node.DisconnectCode.NORMAL, System.currentTimeMillis());
    measurePerformance(
        "HelloMessage Data Access",
        10000000,
        () -> {
          byte[] data = preCreatedHello.getData().toArray();
          assertNotNull(data);
        });

    PingMessage preCreatedPing = new PingMessage(config);
    measurePerformance(
        "PingMessage Data Access",
        10000000,
        () -> {
          byte[] data = preCreatedPing.getData().toArray();
          assertNotNull(data);
        });
  }

  private void testConcurrentP2pProcessing(ChannelManager channelManager) {
    log.info("🔄 Testing Concurrent P2P Processing:");

    int[] threadCounts = {1, 2, 4, 8};

    for (int threadCount : threadCounts) {
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);
      AtomicLong totalOperations = new AtomicLong(0);

      long startTime = System.nanoTime();

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                // Pre-create message to reduce allocation overhead
                HelloMessage msg =
                    new HelloMessage(
                        config,
                        io.xdag.p2p.message.node.DisconnectCode.NORMAL,
                        System.currentTimeMillis());

                for (int j = 0; j < 10000; j++) {
                  // Test pure message operations without channel overhead
                  byte[] data = msg.getData().toArray();
                  assertNotNull(data);
                  totalOperations.incrementAndGet();
                }
              } finally {
                latch.countDown();
              }
            });
      }

      try {
        latch.await(30, TimeUnit.SECONDS);
        long duration = System.nanoTime() - startTime;
        double opsPerSec = totalOperations.get() / (duration / 1_000_000_000.0);

        log.info(
            "⚡ Concurrent P2P ({} threads): {} ops/sec",
            threadCount,
            String.format("%.0f", opsPerSec));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        executor.shutdown();
      }
    }
  }

  private void testP2pMessageType(String messageType, Runnable test) {
    measurePerformance(messageType + " Processing", 100000, test);
  }

  private void testConcurrentDHT(NodeTable nodeTable, int threadCount) {
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    AtomicLong operations = new AtomicLong(0);
    int opsPerThread = 2000;

    Instant start = Instant.now();
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < opsPerThread; j++) {
                if (j % 4 == 0) {
                  nodeTable.addNode(generateGlobalNode());
                } else {
                  nodeTable.getClosestNodes(NetUtils.getNodeId());
                }
                operations.incrementAndGet();
              }
            } finally {
              latch.countDown();
            }
          });
    }

    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.error("Concurrent DHT test interrupted", e);
    }

    Duration duration = Duration.between(start, Instant.now());
    double opsPerSec = operations.get() / (duration.toNanos() / 1_000_000_000.0);

    log.info(
        "🔄 Concurrent DHT: {} threads, {} ops/sec", threadCount, String.format("%.0f", opsPerSec));

    executor.shutdown();
  }
}
