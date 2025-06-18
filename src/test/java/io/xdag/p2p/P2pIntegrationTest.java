package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for P2P network functionality. Tests basic multi-node connections and service
 * lifecycle.
 *
 * <p>Note: These integration tests are skipped in CI environments due to network restrictions.
 * To run integration tests locally, set system property: -Drun.integration.tests=true
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Slf4j(topic = "integration-test")
public class P2pIntegrationTest {

  private P2pService node1;
  private P2pService node2;
  private P2pConfig config1;
  private P2pConfig config2;

  @BeforeEach
  void setUp() {
    // Skip integration tests in CI environments unless explicitly enabled
    boolean isCI = System.getenv("CI") != null;
    boolean runIntegrationTests = Boolean.parseBoolean(System.getProperty("run.integration.tests", "false"));
    
    assumeFalse(isCI && !runIntegrationTests, 
        "Integration tests are skipped in CI environment. Use -Drun.integration.tests=true to run them.");

    // Configure first node
    config1 = new P2pConfig();
    config1.setPort(17001);
    config1.setDiscoverEnable(false);
    node1 = new P2pService(config1);

    // Configure second node
    config2 = new P2pConfig();
    config2.setPort(17002);
    config2.setDiscoverEnable(false);
    node2 = new P2pService(config2);
    
    log.info("Integration test setup completed - running in local environment");
  }

  @AfterEach
  void tearDown() {
    if (node1 != null && !node1.isShutdown()) {
      node1.close();
    }
    if (node2 != null && !node2.isShutdown()) {
      node2.close();
    }
  }

  /** Test basic two-node connection establishment. */
  @Test
  @Timeout(30)
  void testTwoNodeConnection() throws InterruptedException {
    log.info("Starting two-node connection test");

    // Start both nodes
    node1.start();
    node2.start();

    // Wait for nodes to be ready
    Thread.sleep(500); // Reduced from 1000ms

    // Connect node2 to node1
    InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
    Node node1Info = new Node(config2, node1Address);

    // Add node1 as a peer to node2
    node2.connect(node1Info, null);

    // Wait for connection to establish with multiple attempts
    boolean connectionEstablished = false;
    for (int i = 0; i < 5; i++) {
      Thread.sleep(1000);
      int node1Channels = node1.getChannelManager().getChannels().size();
      int node2Channels = node2.getChannelManager().getChannels().size();
      if (node1Channels > 0 || node2Channels > 0) {
        connectionEstablished = true;
        log.info("Connection established after {} seconds. Node1: {}, Node2: {}", 
                 i + 1, node1Channels, node2Channels);
        break;
      }
    }

    assertTrue(connectionEstablished, "At least one node should have active connections");

    log.info("Two-node connection test completed successfully");
  }

  /** Test connection recovery after temporary disconnection. */
  @Test
  @Timeout(45)
  void testConnectionRecovery() throws InterruptedException {
    log.info("Starting connection recovery test");

    // Start nodes and establish connection
    node1.start();
    node2.start();
    Thread.sleep(500); // Reduced from 1000ms

    InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
    Node node1Info = new Node(config2, node1Address);
    node2.connect(node1Info, null);
    Thread.sleep(1000); // Reduced from 2000ms

    // Verify initial connection
    int initialNode1Connections = node1.getChannelManager().getChannels().size();
    int initialNode2Connections = node2.getChannelManager().getChannels().size();
    assertTrue(
        initialNode1Connections > 0 || initialNode2Connections > 0,
        "Initial connection should be established");

    // Simulate disconnection by stopping node1
    log.info("Simulating disconnection by stopping node1");
    node1.close();
    Thread.sleep(1500); // Reduced from 3000ms

    // Verify disconnection
    int disconnectedNode2Connections = node2.getChannelManager().getChannels().size();
    log.info("Node2 connections after node1 shutdown: {}", disconnectedNode2Connections);

    // Restart node1
    log.info("Restarting node1");
    node1 = new P2pService(config1);
    node1.start();
    Thread.sleep(1000); // Reduced from 2000ms

    // Attempt reconnection
    node2.connect(node1Info, null);
    Thread.sleep(1500); // Reduced from 3000ms

    // Verify reconnection
    int recoveredNode1Connections = node1.getChannelManager().getChannels().size();
    int recoveredNode2Connections = node2.getChannelManager().getChannels().size();
    assertTrue(
        recoveredNode1Connections > 0 || recoveredNode2Connections > 0,
        "Connection should be recovered after restart");

    log.info("Connection recovery test completed successfully");
  }

  /** Test multiple concurrent connections. */
  @Test
  @Timeout(30)
  void testMultipleConnections() throws InterruptedException {
    log.info("Starting multiple connections test");

    // Create third node
    P2pConfig config3 = new P2pConfig();
    config3.setPort(17003);
    config3.setDiscoverEnable(false);
    P2pService node3 = new P2pService(config3);

    try {
      // Start all nodes
      node1.start();
      node2.start();
      node3.start();
      Thread.sleep(1000);

      // Connect node2 and node3 to node1
      InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
      Node node1Info2 = new Node(config2, node1Address);
      Node node1Info3 = new Node(config3, node1Address);

      node2.connect(node1Info2, null);
      node3.connect(node1Info3, null);
      
      // Wait for connections to establish with retry logic
      boolean hasConnections = false;
      for (int i = 0; i < 5; i++) {
        Thread.sleep(1000);
        int node1Channels = node1.getChannelManager().getChannels().size();
        log.info("Attempt {}: Node1 has {} active connections", i + 1, node1Channels);
        if (node1Channels >= 1) {
          hasConnections = true;
          break;
        }
      }

      assertTrue(hasConnections, "Node1 should have at least one connection");

      log.info("Multiple connections test completed successfully");
    } finally {
      if (node3 != null && !node3.isShutdown()) {
        node3.close();
      }
    }
  }

  /** Test node discovery and connection establishment. */
  @Test
  @Timeout(30)
  void testNodeDiscoveryAndConnection() throws InterruptedException {
    log.info("Starting node discovery and connection test");

    // Start nodes
    node1.start();
    node2.start();
    Thread.sleep(1000);

    // Add node1 as a known peer to node2
    InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
    Node node1Info = new Node(config2, node1Address);

    // Test node validation
    assertTrue(node1Info.getPort() > 0, "Node should have valid port");
    assertNotNull(node1Info.getId(), "Node should have valid ID");

    // Connect and verify
    node2.connect(node1Info, null);
    Thread.sleep(2000);

    // Check if connection was established
    boolean connectionEstablished =
        node1.getChannelManager().getChannels().size() > 0
            || node2.getChannelManager().getChannels().size() > 0;

    assertTrue(connectionEstablished, "Connection should be established through discovery");

    log.info("Node discovery and connection test completed successfully");
  }

  /** Test graceful shutdown of connected nodes. */
  @Test
  @Timeout(30)
  void testGracefulShutdown() throws InterruptedException {
    log.info("Starting graceful shutdown test");

    // Start and connect nodes
    node1.start();
    node2.start();
    Thread.sleep(1000);

    InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
    Node node1Info = new Node(config2, node1Address);
    node2.connect(node1Info, null);
    Thread.sleep(2000);

    // Verify connection
    assertTrue(
        node1.getChannelManager().getChannels().size() > 0
            || node2.getChannelManager().getChannels().size() > 0,
        "Connection should be established");

    // Test graceful shutdown
    assertFalse(node1.isShutdown(), "Node1 should not be shutdown initially");
    assertFalse(node2.isShutdown(), "Node2 should not be shutdown initially");

    node1.close();
    assertTrue(node1.isShutdown(), "Node1 should be shutdown after close()");

    node2.close();
    assertTrue(node2.isShutdown(), "Node2 should be shutdown after close()");

    log.info("Graceful shutdown test completed successfully");
  }

  /** Test service configuration and initialization. */
  @Test
  @Timeout(15)
  void testServiceConfiguration() throws InterruptedException {
    log.info("Starting service configuration test");

    // Test configuration validation
    assertEquals(17001, config1.getPort(), "Node1 should have correct port");
    assertEquals(17002, config2.getPort(), "Node2 should have correct port");
    assertFalse(config1.isDiscoverEnable(), "Discovery should be disabled for testing");
    assertFalse(config2.isDiscoverEnable(), "Discovery should be disabled for testing");

    // Test service initialization
    assertNotNull(node1.getChannelManager(), "Node1 should have channel manager");
    assertNotNull(node2.getChannelManager(), "Node2 should have channel manager");

    // Test service startup
    node1.start();
    node2.start();
    Thread.sleep(1000);

    assertFalse(node1.isShutdown(), "Node1 should be running after start");
    assertFalse(node2.isShutdown(), "Node2 should be running after start");

    log.info("Service configuration test completed successfully");
  }

  /** Test concurrent service operations. */
  @Test
  @Timeout(20)
  void testConcurrentOperations() throws InterruptedException {
    log.info("Starting concurrent operations test");

    // Start services concurrently
    Thread startThread1 = new Thread(() -> node1.start());
    Thread startThread2 = new Thread(() -> node2.start());

    startThread1.start();
    startThread2.start();

    startThread1.join();
    startThread2.join();

    Thread.sleep(1000);

    // Verify both services started successfully
    assertFalse(node1.isShutdown(), "Node1 should be running");
    assertFalse(node2.isShutdown(), "Node2 should be running");

    // Test concurrent connection attempts
    InetSocketAddress node1Address = new InetSocketAddress("127.0.0.1", config1.getPort());
    Node node1Info = new Node(config2, node1Address);

    Thread connectThread = new Thread(() -> node2.connect(node1Info, null));
    connectThread.start();
    connectThread.join();

    Thread.sleep(3000); // Increased from 2000ms for more reliable connection establishment

    // Verify connection was established
    assertTrue(
        node1.getChannelManager().getChannels().size() > 0
            || node2.getChannelManager().getChannels().size() > 0,
        "Connection should be established");

    log.info("Concurrent operations test completed successfully");
  }
}
