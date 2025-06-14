package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DisconnectCode enum. Tests code mapping, uniqueness, and conversion from integer.
 */
public class DisconnectCodeTest {

  @Test
  void testForNumber_KnownCodes() {
    assertEquals(DisconnectCode.NORMAL, DisconnectCode.forNumber(0), "0 should map to NORMAL");
    assertEquals(
        DisconnectCode.TOO_MANY_PEERS,
        DisconnectCode.forNumber(1),
        "1 should map to TOO_MANY_PEERS");
    assertEquals(
        DisconnectCode.DIFFERENT_VERSION,
        DisconnectCode.forNumber(2),
        "2 should map to DIFFERENT_VERSION");
    assertEquals(
        DisconnectCode.TIME_BANNED, DisconnectCode.forNumber(3), "3 should map to TIME_BANNED");
    assertEquals(
        DisconnectCode.DUPLICATE_PEER,
        DisconnectCode.forNumber(4),
        "4 should map to DUPLICATE_PEER");
    assertEquals(
        DisconnectCode.MAX_CONNECTION_WITH_SAME_IP,
        DisconnectCode.forNumber(5),
        "5 should map to MAX_CONNECTION_WITH_SAME_IP");
    assertEquals(
        DisconnectCode.UNKNOWN, DisconnectCode.forNumber(256), "256 should map to UNKNOWN");
  }

  @Test
  void testForNumber_UnknownCode() {
    assertEquals(DisconnectCode.UNKNOWN, DisconnectCode.forNumber(-1), "-1 should map to UNKNOWN");
    assertEquals(
        DisconnectCode.UNKNOWN, DisconnectCode.forNumber(100), "100 should map to UNKNOWN");
    assertEquals(
        DisconnectCode.UNKNOWN,
        DisconnectCode.forNumber(Integer.MAX_VALUE),
        "Max int should map to UNKNOWN");
  }

  @Test
  void testGetValue() {
    assertEquals(0, DisconnectCode.NORMAL.getValue(), "NORMAL value should be 0");
    assertEquals(1, DisconnectCode.TOO_MANY_PEERS.getValue(), "TOO_MANY_PEERS value should be 1");
    assertEquals(
        2, DisconnectCode.DIFFERENT_VERSION.getValue(), "DIFFERENT_VERSION value should be 2");
    assertEquals(3, DisconnectCode.TIME_BANNED.getValue(), "TIME_BANNED value should be 3");
    assertEquals(4, DisconnectCode.DUPLICATE_PEER.getValue(), "DUPLICATE_PEER value should be 4");
    assertEquals(
        5,
        DisconnectCode.MAX_CONNECTION_WITH_SAME_IP.getValue(),
        "MAX_CONNECTION_WITH_SAME_IP value should be 5");
    assertEquals(256, DisconnectCode.UNKNOWN.getValue(), "UNKNOWN value should be 256");
  }

  @Test
  void testAllCodesCoveredInForNumber() {
    for (DisconnectCode code : DisconnectCode.values()) {
      assertEquals(
          code,
          DisconnectCode.forNumber(code.getValue()),
          "Code " + code.name() + " should be retrievable from its integer value");
    }
  }

  @Test
  void testEnumValues() {
    assertNotNull(DisconnectCode.values(), "Should be able to get all enum values");
    assertTrue(DisconnectCode.values().length > 0, "There should be at least one DisconnectCode");
  }

  @Test
  void testUniquenessOfValues() {
    Set<Integer> values = new HashSet<>();
    for (DisconnectCode code : DisconnectCode.values()) {
      assertTrue(
          values.add(code.getValue()),
          "Value " + code.getValue() + " for " + code.name() + " should be unique");
    }
  }

  @Test
  void testToString() {
    for (DisconnectCode code : DisconnectCode.values()) {
      assertNotNull(code.toString(), "toString() should not be null for " + code.name());
      assertEquals(code.name(), code.toString(), "toString() should return the enum's name");
    }
  }
}
