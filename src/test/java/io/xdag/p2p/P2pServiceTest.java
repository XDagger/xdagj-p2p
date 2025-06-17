package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.stats.P2pStats;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for P2pService core functionality. These are true unit tests that test logic without
 * starting real services.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Slf4j(topic = "test")
public class P2pServiceTest {

  private P2pService p2pService;
  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setPort(16783);
    p2pConfig.setDiscoverEnable(false); // Disable discovery for unit tests
    p2pService = new P2pService(p2pConfig);
  }

  @AfterEach
  void tearDown() {
    if (p2pService != null && !p2pService.isShutdown()) {
      p2pService.close();
    }
  }

  /** Test P2P service initialization and basic getters. */
  @Test
  void testServiceInitialization() {
    assertNotNull(p2pService.getP2pConfig(), "P2P config should not be null");
    assertNotNull(p2pService.getNodeManager(), "Node manager should not be null");
    assertNotNull(p2pService.getDnsManager(), "DNS manager should not be null");
    assertNotNull(p2pService.getChannelManager(), "Channel manager should not be null");
    assertNotNull(p2pService.getP2pStatsManager(), "Stats manager should not be null");

    // Service is not shutdown initially, it's just not started
    assertFalse(p2pService.isShutdown(), "Service should not be shutdown initially");
    assertEquals(P2pConstant.version, p2pService.getVersion(), "Version should match constant");
  }

  /** Test P2P service configuration. */
  @Test
  void testServiceConfiguration() {
    assertEquals(p2pConfig, p2pService.getP2pConfig(), "Config should match");
    assertEquals(16783, p2pService.getP2pConfig().getPort(), "Port should match");
    assertFalse(p2pService.getP2pConfig().isDiscoverEnable(), "Discovery should be disabled");
  }

  /** Test P2P event handler registration. */
  @Test
  void testEventHandlerRegistration() {
    TestEventHandler handler1 = new TestEventHandler("handler1", (byte) 0x01);
    TestEventHandler handler2 = new TestEventHandler("handler2", (byte) 0x02);

    // Register first handler
    try {
      p2pService.register(handler1);
    } catch (P2pException e) {
      fail("First handler registration should succeed");
    }

    // Register second handler with different message type
    try {
      p2pService.register(handler2);
    } catch (P2pException e) {
      fail("Second handler registration should succeed");
    }

    // Verify handlers are registered in config
    assertTrue(p2pConfig.getHandlerList().contains(handler1), "First handler should be registered");
    assertTrue(
        p2pConfig.getHandlerList().contains(handler2), "Second handler should be registered");
    assertEquals(2, p2pConfig.getHandlerList().size(), "Should have exactly 2 handlers registered");
  }

  /** Test P2P event handler registration with duplicate message type. */
  @Test
  void testEventHandlerRegistrationDuplicate() {
    TestEventHandler handler1 = new TestEventHandler("handler1", (byte) 0x01);
    TestEventHandler handler2 = new TestEventHandler("handler2", (byte) 0x01); // Same type

    // Register first handler
    try {
      p2pService.register(handler1);
    } catch (P2pException e) {
      fail("First handler registration should succeed");
    }

    // Register second handler with same message type should fail
    try {
      p2pService.register(handler2);
      fail("Second handler registration with duplicate type should fail");
    } catch (P2pException e) {
      // Expected exception
    }

    // Verify only first handler is registered
    assertTrue(p2pConfig.getHandlerList().contains(handler1), "First handler should be registered");
    assertFalse(
        p2pConfig.getHandlerList().contains(handler2), "Second handler should not be registered");
    assertEquals(1, p2pConfig.getHandlerList().size(), "Should have exactly 1 handler registered");
  }

  /** Test P2P statistics functionality. */
  @Test
  void testP2pStats() {
    P2pStats stats = p2pService.getP2pStats();
    assertNotNull(stats, "P2P stats should not be null");

    // Stats should be accessible even before service start
    assertNotNull(stats.toString(), "Stats toString should not be null");
  }

  /** Test node management functionality without starting service. */
  @Test
  void testNodeManagementWithoutStart() {
    // Some methods may not work without initialization, so test basic structure
    assertNotNull(p2pService.getNodeManager(), "Node manager should not be null");

    // Test that we can get stats without starting
    assertNotNull(p2pService.getP2pStats(), "P2P stats should be accessible");
  }

  /** Test service shutdown state. */
  @Test
  void testServiceShutdownState() {
    assertFalse(p2pService.isShutdown(), "Service should not be shutdown initially");

    // Close the service
    p2pService.close();
    assertTrue(p2pService.isShutdown(), "Service should be shutdown after close");

    // Multiple close calls should be safe
    assertDoesNotThrow(() -> p2pService.close(), "Multiple close calls should be safe");
    assertTrue(p2pService.isShutdown(), "Service should remain shutdown");
  }

  /** Test service component creation. */
  @Test
  void testServiceComponents() {
    // Test that all components are properly created
    assertNotNull(p2pService.getNodeManager(), "NodeManager should be created");
    assertNotNull(p2pService.getDnsManager(), "DnsManager should be created");
    assertNotNull(p2pService.getChannelManager(), "ChannelManager should be created");
    assertNotNull(p2pService.getP2pStatsManager(), "P2pStatsManager should be created");

    // Test that components have proper configuration
    assertEquals(p2pConfig, p2pService.getP2pConfig(), "All components should use same config");
  }

  /** Test handler list management. */
  @Test
  void testHandlerListManagement() {
    assertEquals(0, p2pConfig.getHandlerList().size(), "Handler list should be empty initially");

    TestEventHandler handler = new TestEventHandler("test", (byte) 0x01);
    try {
      p2pService.register(handler);
    } catch (P2pException e) {
      fail("Handler registration should succeed");
    }

    assertEquals(1, p2pConfig.getHandlerList().size(), "Handler list should have one handler");
    assertTrue(p2pConfig.getHandlerList().contains(handler), "Handler should be in list");
  }

  /** Test version information. */
  @Test
  void testVersionInfo() {
    int version = p2pService.getVersion();
    assertEquals(P2pConstant.version, version, "Version should match constant");
  }

  /** Test event handler implementation. */
  private static class TestEventHandler extends P2pEventHandler {
    private final String name;

    public TestEventHandler(String name, byte messageType) {
      this.name = name;
      this.messageTypes = new java.util.HashSet<>();
      this.messageTypes.add(messageType);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
