package io.xdag.p2p.stats;

import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for P2P statistics functionality. Tests P2pStats data structure and P2pStatsManager
 * collection functionality.
 *
 * @author XDAG Team
 * @since 0.1
 */
@Slf4j(topic = "test")
public class P2pStatsTest {

  private P2pStats p2pStats;
  private P2pStatsManager p2pStatsManager;

  @BeforeEach
  void setUp() {
    p2pStats = new P2pStats();
    p2pStatsManager = new P2pStatsManager();
  }

  /** Test P2pStats default values. */
  @Test
  void testP2pStatsDefaultValues() {
    assertEquals(0L, p2pStats.getTcpOutSize(), "TCP out size should default to 0");
    assertEquals(0L, p2pStats.getTcpInSize(), "TCP in size should default to 0");
    assertEquals(0L, p2pStats.getTcpOutPackets(), "TCP out packets should default to 0");
    assertEquals(0L, p2pStats.getTcpInPackets(), "TCP in packets should default to 0");
    assertEquals(0L, p2pStats.getUdpOutSize(), "UDP out size should default to 0");
    assertEquals(0L, p2pStats.getUdpInSize(), "UDP in size should default to 0");
    assertEquals(0L, p2pStats.getUdpOutPackets(), "UDP out packets should default to 0");
    assertEquals(0L, p2pStats.getUdpInPackets(), "UDP in packets should default to 0");
  }

  /** Test P2pStats setters and getters. */
  @Test
  void testP2pStatsSettersAndGetters() {
    // Test TCP statistics
    p2pStats.setTcpOutSize(1024L);
    p2pStats.setTcpInSize(2048L);
    p2pStats.setTcpOutPackets(10L);
    p2pStats.setTcpInPackets(15L);

    assertEquals(1024L, p2pStats.getTcpOutSize(), "TCP out size should be set correctly");
    assertEquals(2048L, p2pStats.getTcpInSize(), "TCP in size should be set correctly");
    assertEquals(10L, p2pStats.getTcpOutPackets(), "TCP out packets should be set correctly");
    assertEquals(15L, p2pStats.getTcpInPackets(), "TCP in packets should be set correctly");

    // Test UDP statistics
    p2pStats.setUdpOutSize(512L);
    p2pStats.setUdpInSize(1536L);
    p2pStats.setUdpOutPackets(5L);
    p2pStats.setUdpInPackets(8L);

    assertEquals(512L, p2pStats.getUdpOutSize(), "UDP out size should be set correctly");
    assertEquals(1536L, p2pStats.getUdpInSize(), "UDP in size should be set correctly");
    assertEquals(5L, p2pStats.getUdpOutPackets(), "UDP out packets should be set correctly");
    assertEquals(8L, p2pStats.getUdpInPackets(), "UDP in packets should be set correctly");
  }

  /** Test P2pStats with large values. */
  @Test
  void testP2pStatsLargeValues() {
    long largeValue = Long.MAX_VALUE;

    p2pStats.setTcpOutSize(largeValue);
    p2pStats.setTcpInSize(largeValue - 1);
    p2pStats.setTcpOutPackets(largeValue / 2);
    p2pStats.setTcpInPackets(largeValue / 3);

    assertEquals(largeValue, p2pStats.getTcpOutSize(), "Should handle large TCP out size");
    assertEquals(largeValue - 1, p2pStats.getTcpInSize(), "Should handle large TCP in size");
    assertEquals(
        largeValue / 2, p2pStats.getTcpOutPackets(), "Should handle large TCP out packets");
    assertEquals(largeValue / 3, p2pStats.getTcpInPackets(), "Should handle large TCP in packets");
  }

  /** Test P2pStats with negative values (edge case). */
  @Test
  void testP2pStatsNegativeValues() {
    // While negative values don't make logical sense for statistics,
    // the data structure should handle them without errors
    p2pStats.setTcpOutSize(-100L);
    p2pStats.setUdpInPackets(-50L);

    assertEquals(-100L, p2pStats.getTcpOutSize(), "Should handle negative TCP out size");
    assertEquals(-50L, p2pStats.getUdpInPackets(), "Should handle negative UDP in packets");
  }

  /** Test P2pStatsManager basic functionality. */
  @Test
  void testP2pStatsManager() {
    P2pStats stats = p2pStatsManager.getP2pStats();

    assertNotNull(stats, "P2pStatsManager should return non-null stats");

    // Verify all fields are accessible
    assertNotNull(stats.getTcpOutSize(), "TCP out size should not be null");
    assertNotNull(stats.getTcpInSize(), "TCP in size should not be null");
    assertNotNull(stats.getTcpOutPackets(), "TCP out packets should not be null");
    assertNotNull(stats.getTcpInPackets(), "TCP in packets should not be null");
    assertNotNull(stats.getUdpOutSize(), "UDP out size should not be null");
    assertNotNull(stats.getUdpInSize(), "UDP in size should not be null");
    assertNotNull(stats.getUdpOutPackets(), "UDP out packets should not be null");
    assertNotNull(stats.getUdpInPackets(), "UDP in packets should not be null");

    // All values should be non-negative (statistics should not be negative)
    assertTrue(stats.getTcpOutSize() >= 0, "TCP out size should be non-negative");
    assertTrue(stats.getTcpInSize() >= 0, "TCP in size should be non-negative");
    assertTrue(stats.getTcpOutPackets() >= 0, "TCP out packets should be non-negative");
    assertTrue(stats.getTcpInPackets() >= 0, "TCP in packets should be non-negative");
    assertTrue(stats.getUdpOutSize() >= 0, "UDP out size should be non-negative");
    assertTrue(stats.getUdpInSize() >= 0, "UDP in size should be non-negative");
    assertTrue(stats.getUdpOutPackets() >= 0, "UDP out packets should be non-negative");
    assertTrue(stats.getUdpInPackets() >= 0, "UDP in packets should be non-negative");
  }

  /** Test P2pStatsManager returns fresh stats each time. */
  @Test
  void testP2pStatsManagerFreshStats() {
    P2pStats stats1 = p2pStatsManager.getP2pStats();
    P2pStats stats2 = p2pStatsManager.getP2pStats();

    assertNotNull(stats1, "First stats should not be null");
    assertNotNull(stats2, "Second stats should not be null");

    // Should return different instances (fresh stats each time)
    assertNotSame(stats1, stats2, "Should return different instances each time");
  }

  /** Test P2pStats object equality and hash code. */
  @Test
  void testP2pStatsEquality() {
    P2pStats stats1 = new P2pStats();
    P2pStats stats2 = new P2pStats();

    // Set same values
    stats1.setTcpOutSize(1000L);
    stats1.setTcpInSize(2000L);
    stats1.setUdpOutPackets(10L);

    stats2.setTcpOutSize(1000L);
    stats2.setTcpInSize(2000L);
    stats2.setUdpOutPackets(10L);

    // Note: P2pStats doesn't override equals/hashCode, so they use Object's implementation
    // This test verifies the current behavior
    assertNotEquals(stats1, stats2, "Different instances should not be equal (no equals override)");
    assertNotEquals(
        stats1.hashCode(),
        stats2.hashCode(),
        "Different instances should have different hash codes");
  }

  /** Test P2pStats toString functionality. */
  @Test
  void testP2pStatsToString() {
    p2pStats.setTcpOutSize(1024L);
    p2pStats.setTcpInSize(2048L);
    p2pStats.setTcpOutPackets(10L);
    p2pStats.setTcpInPackets(15L);
    p2pStats.setUdpOutSize(512L);
    p2pStats.setUdpInSize(1536L);
    p2pStats.setUdpOutPackets(5L);
    p2pStats.setUdpInPackets(8L);

    String toString = p2pStats.toString();
    assertNotNull(toString, "toString should not return null");
    assertFalse(toString.isEmpty(), "toString should not be empty");

    // Should contain class name (Lombok generates toString with class name)
    assertTrue(toString.contains("P2pStats"), "toString should contain class name");
  }

  /** Test P2pStats field independence. */
  @Test
  void testP2pStatsFieldIndependence() {
    // Set one field and verify others remain unchanged
    p2pStats.setTcpOutSize(1000L);

    assertEquals(1000L, p2pStats.getTcpOutSize(), "TCP out size should be set");
    assertEquals(0L, p2pStats.getTcpInSize(), "TCP in size should remain 0");
    assertEquals(0L, p2pStats.getTcpOutPackets(), "TCP out packets should remain 0");
    assertEquals(0L, p2pStats.getTcpInPackets(), "TCP in packets should remain 0");
    assertEquals(0L, p2pStats.getUdpOutSize(), "UDP out size should remain 0");
    assertEquals(0L, p2pStats.getUdpInSize(), "UDP in size should remain 0");
    assertEquals(0L, p2pStats.getUdpOutPackets(), "UDP out packets should remain 0");
    assertEquals(0L, p2pStats.getUdpInPackets(), "UDP in packets should remain 0");
  }

  /** Test P2pStats with zero values. */
  @Test
  void testP2pStatsZeroValues() {
    // Explicitly set all values to zero
    p2pStats.setTcpOutSize(0L);
    p2pStats.setTcpInSize(0L);
    p2pStats.setTcpOutPackets(0L);
    p2pStats.setTcpInPackets(0L);
    p2pStats.setUdpOutSize(0L);
    p2pStats.setUdpInSize(0L);
    p2pStats.setUdpOutPackets(0L);
    p2pStats.setUdpInPackets(0L);

    assertEquals(0L, p2pStats.getTcpOutSize(), "TCP out size should be 0");
    assertEquals(0L, p2pStats.getTcpInSize(), "TCP in size should be 0");
    assertEquals(0L, p2pStats.getTcpOutPackets(), "TCP out packets should be 0");
    assertEquals(0L, p2pStats.getTcpInPackets(), "TCP in packets should be 0");
    assertEquals(0L, p2pStats.getUdpOutSize(), "UDP out size should be 0");
    assertEquals(0L, p2pStats.getUdpInSize(), "UDP in size should be 0");
    assertEquals(0L, p2pStats.getUdpOutPackets(), "UDP out packets should be 0");
    assertEquals(0L, p2pStats.getUdpInPackets(), "UDP in packets should be 0");
  }

  /** Test multiple P2pStatsManager instances. */
  @Test
  void testMultipleP2pStatsManagers() {
    P2pStatsManager manager1 = new P2pStatsManager();
    P2pStatsManager manager2 = new P2pStatsManager();

    P2pStats stats1 = manager1.getP2pStats();
    P2pStats stats2 = manager2.getP2pStats();

    assertNotNull(stats1, "First manager should return stats");
    assertNotNull(stats2, "Second manager should return stats");
    assertNotSame(stats1, stats2, "Different managers should return different stats instances");
  }
}
