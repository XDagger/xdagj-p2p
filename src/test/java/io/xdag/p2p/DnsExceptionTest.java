package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DnsException class. Tests DNS exception creation, message handling, type
 * enumeration, and exception chaining.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Slf4j(topic = "test")
public class DnsExceptionTest {

  /** Test DnsException creation with type and message. */
  @Test
  void testExceptionWithTypeAndMessage() {
    String errorMessage = "Test DNS error message";
    DnsException exception = new DnsException(DnsException.TypeEnum.NO_ROOT_FOUND, errorMessage);

    assertEquals(
        DnsException.TypeEnum.NO_ROOT_FOUND, exception.getType(), "Exception type should match");
    assertTrue(
        exception.getMessage().contains(DnsException.TypeEnum.NO_ROOT_FOUND.getDesc()),
        "Exception message should contain type description");
    assertTrue(
        exception.getMessage().contains(errorMessage),
        "Exception message should contain custom message");
    assertNull(exception.getCause(), "Exception should have no cause");
  }

  /** Test DnsException creation with type and throwable cause. */
  @Test
  void testExceptionWithTypeAndCause() {
    RuntimeException cause = new RuntimeException("DNS root cause");
    DnsException exception = new DnsException(DnsException.TypeEnum.HASH_MISS_MATCH, cause);

    assertEquals(
        DnsException.TypeEnum.HASH_MISS_MATCH, exception.getType(), "Exception type should match");
    assertEquals(cause, exception.getCause(), "Exception cause should match");
    assertEquals(
        "java.lang.RuntimeException: DNS root cause",
        exception.getMessage(),
        "Exception message should include cause");
  }

  /** Test DnsException creation with type, message and throwable cause. */
  @Test
  void testExceptionWithTypeMessageAndCause() {
    String errorMessage = "Custom DNS error message";
    RuntimeException cause = new RuntimeException("DNS root cause");
    DnsException exception =
        new DnsException(DnsException.TypeEnum.INVALID_NODES, errorMessage, cause);

    assertEquals(
        DnsException.TypeEnum.INVALID_NODES, exception.getType(), "Exception type should match");
    assertEquals(errorMessage, exception.getMessage(), "Exception message should match");
    assertEquals(cause, exception.getCause(), "Exception cause should match");
  }

  /** Test all DnsException type enums. */
  @Test
  void testAllExceptionTypes() {
    // Test each exception type
    DnsException.TypeEnum[] types = DnsException.TypeEnum.values();
    assertTrue(types.length > 0, "Should have at least one exception type");

    for (DnsException.TypeEnum type : types) {
      assertNotNull(type.getValue(), "Type value should not be null");
      assertNotNull(type.getDesc(), "Type description should not be null");
      assertFalse(type.getDesc().isEmpty(), "Type description should not be empty");

      // Test toString format
      String toString = type.toString();
      assertTrue(toString.contains(type.getValue().toString()), "toString should contain value");
      assertTrue(toString.contains(type.getDesc()), "toString should contain description");
    }
  }

  /** Test specific DNS exception types and their properties. */
  @Test
  void testSpecificExceptionTypes() {
    // Test LOOK_UP_ROOT_FAILED
    assertEquals(
        0,
        DnsException.TypeEnum.LOOK_UP_ROOT_FAILED.getValue(),
        "LOOK_UP_ROOT_FAILED should have value 0");
    assertEquals(
        "look up root failed",
        DnsException.TypeEnum.LOOK_UP_ROOT_FAILED.getDesc(),
        "LOOK_UP_ROOT_FAILED should have correct description");

    // Test NO_ROOT_FOUND
    assertEquals(
        1, DnsException.TypeEnum.NO_ROOT_FOUND.getValue(), "NO_ROOT_FOUND should have value 1");
    assertEquals(
        "no valid root found",
        DnsException.TypeEnum.NO_ROOT_FOUND.getDesc(),
        "NO_ROOT_FOUND should have correct description");

    // Test NO_ENTRY_FOUND
    assertEquals(
        2, DnsException.TypeEnum.NO_ENTRY_FOUND.getValue(), "NO_ENTRY_FOUND should have value 2");
    assertEquals(
        "no valid tree entry found",
        DnsException.TypeEnum.NO_ENTRY_FOUND.getDesc(),
        "NO_ENTRY_FOUND should have correct description");

    // Test HASH_MISS_MATCH
    assertEquals(
        3, DnsException.TypeEnum.HASH_MISS_MATCH.getValue(), "HASH_MISS_MATCH should have value 3");
    assertEquals(
        "hash miss match",
        DnsException.TypeEnum.HASH_MISS_MATCH.getDesc(),
        "HASH_MISS_MATCH should have correct description");

    // Test NODES_IN_LINK_TREE
    assertEquals(
        4,
        DnsException.TypeEnum.NODES_IN_LINK_TREE.getValue(),
        "NODES_IN_LINK_TREE should have value 4");
    assertEquals(
        "nodes entry in link tree",
        DnsException.TypeEnum.NODES_IN_LINK_TREE.getDesc(),
        "NODES_IN_LINK_TREE should have correct description");

    // Test LINK_IN_NODES_TREE
    assertEquals(
        5,
        DnsException.TypeEnum.LINK_IN_NODES_TREE.getValue(),
        "LINK_IN_NODES_TREE should have value 5");
    assertEquals(
        "link entry in nodes tree",
        DnsException.TypeEnum.LINK_IN_NODES_TREE.getDesc(),
        "LINK_IN_NODES_TREE should have correct description");

    // Test UNKNOWN_ENTRY
    assertEquals(
        6, DnsException.TypeEnum.UNKNOWN_ENTRY.getValue(), "UNKNOWN_ENTRY should have value 6");
    assertEquals(
        "unknown entry type",
        DnsException.TypeEnum.UNKNOWN_ENTRY.getDesc(),
        "UNKNOWN_ENTRY should have correct description");

    // Test NO_PUBLIC_KEY
    assertEquals(
        7, DnsException.TypeEnum.NO_PUBLIC_KEY.getValue(), "NO_PUBLIC_KEY should have value 7");
    assertEquals(
        "missing public key",
        DnsException.TypeEnum.NO_PUBLIC_KEY.getDesc(),
        "NO_PUBLIC_KEY should have correct description");

    // Test BAD_PUBLIC_KEY
    assertEquals(
        8, DnsException.TypeEnum.BAD_PUBLIC_KEY.getValue(), "BAD_PUBLIC_KEY should have value 8");
    assertEquals(
        "invalid public key",
        DnsException.TypeEnum.BAD_PUBLIC_KEY.getDesc(),
        "BAD_PUBLIC_KEY should have correct description");

    // Test INVALID_NODES
    assertEquals(
        9, DnsException.TypeEnum.INVALID_NODES.getValue(), "INVALID_NODES should have value 9");
    assertEquals(
        "invalid node list",
        DnsException.TypeEnum.INVALID_NODES.getDesc(),
        "INVALID_NODES should have correct description");

    // Test INVALID_CHILD
    assertEquals(
        10, DnsException.TypeEnum.INVALID_CHILD.getValue(), "INVALID_CHILD should have value 10");
    assertEquals(
        "invalid child hash",
        DnsException.TypeEnum.INVALID_CHILD.getDesc(),
        "INVALID_CHILD should have correct description");

    // Test INVALID_SIGNATURE
    assertEquals(
        11,
        DnsException.TypeEnum.INVALID_SIGNATURE.getValue(),
        "INVALID_SIGNATURE should have value 11");
    assertEquals(
        "invalid base64 signature",
        DnsException.TypeEnum.INVALID_SIGNATURE.getDesc(),
        "INVALID_SIGNATURE should have correct description");

    // Test INVALID_ROOT
    assertEquals(
        12, DnsException.TypeEnum.INVALID_ROOT.getValue(), "INVALID_ROOT should have value 12");
    assertEquals(
        "invalid DnsRoot proto",
        DnsException.TypeEnum.INVALID_ROOT.getDesc(),
        "INVALID_ROOT should have correct description");

    // Test INVALID_SCHEME_URL
    assertEquals(
        13,
        DnsException.TypeEnum.INVALID_SCHEME_URL.getValue(),
        "INVALID_SCHEME_URL should have value 13");
    assertEquals(
        "invalid scheme url",
        DnsException.TypeEnum.INVALID_SCHEME_URL.getDesc(),
        "INVALID_SCHEME_URL should have correct description");

    // Test DEPLOY_DOMAIN_FAILED
    assertEquals(
        14,
        DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED.getValue(),
        "DEPLOY_DOMAIN_FAILED should have value 14");
    assertEquals(
        "failed to deploy domain",
        DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED.getDesc(),
        "DEPLOY_DOMAIN_FAILED should have correct description");

    // Test OTHER_ERROR
    assertEquals(
        15, DnsException.TypeEnum.OTHER_ERROR.getValue(), "OTHER_ERROR should have value 15");
    assertEquals(
        "other error",
        DnsException.TypeEnum.OTHER_ERROR.getDesc(),
        "OTHER_ERROR should have correct description");
  }

  /** Test DNS exception type toString format. */
  @Test
  void testExceptionTypeToString() {
    DnsException.TypeEnum type = DnsException.TypeEnum.NO_ROOT_FOUND;
    String toString = type.toString();

    assertEquals(
        "1-no valid root found", toString, "toString should follow 'value-description' format");
  }

  /** Test DNS exception inheritance and instanceof checks. */
  @Test
  void testExceptionInheritance() {
    DnsException exception = new DnsException(DnsException.TypeEnum.INVALID_NODES, "test");

    assertTrue(exception instanceof Exception, "DnsException should be instance of Exception");
    assertTrue(exception instanceof Throwable, "DnsException should be instance of Throwable");
  }

  /** Test DNS exception with null message. */
  @Test
  void testExceptionWithNullMessage() {
    DnsException exception = new DnsException(DnsException.TypeEnum.OTHER_ERROR, (String) null);

    assertEquals(
        DnsException.TypeEnum.OTHER_ERROR, exception.getType(), "Exception type should match");
    assertTrue(
        exception.getMessage().contains(DnsException.TypeEnum.OTHER_ERROR.getDesc()),
        "Exception message should contain type description even with null custom message");
  }

  /** Test DNS exception with empty message. */
  @Test
  void testExceptionWithEmptyMessage() {
    String emptyMessage = "";
    DnsException exception = new DnsException(DnsException.TypeEnum.UNKNOWN_ENTRY, emptyMessage);

    assertEquals(
        DnsException.TypeEnum.UNKNOWN_ENTRY, exception.getType(), "Exception type should match");
    assertTrue(
        exception.getMessage().contains(DnsException.TypeEnum.UNKNOWN_ENTRY.getDesc()),
        "Exception message should contain type description");
    assertTrue(
        exception.getMessage().contains(emptyMessage),
        "Exception message should contain empty custom message");
  }

  /** Test DNS exception chaining with multiple levels. */
  @Test
  void testExceptionChaining() {
    // Create a chain of exceptions
    RuntimeException rootCause = new RuntimeException("DNS root cause");
    IllegalArgumentException intermediateCause =
        new IllegalArgumentException("DNS intermediate cause", rootCause);
    DnsException topException =
        new DnsException(
            DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED, "DNS top level error", intermediateCause);

    assertEquals(
        DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED,
        topException.getType(),
        "Top exception type should match");
    assertEquals(
        "DNS top level error", topException.getMessage(), "Top exception message should match");
    assertEquals(
        intermediateCause, topException.getCause(), "Top exception cause should be intermediate");
    assertEquals(
        rootCause,
        topException.getCause().getCause(),
        "Intermediate exception cause should be root");
  }

  /** Test DNS exception serialization compatibility. */
  @Test
  void testExceptionSerialization() {
    DnsException exception =
        new DnsException(DnsException.TypeEnum.BAD_PUBLIC_KEY, "Serialization test");

    // Basic checks that would be needed for serialization
    assertNotNull(exception.getType(), "Type should not be null for serialization");
    assertNotNull(exception.getMessage(), "Message should not be null for serialization");

    // Test that the exception can be converted to string
    String exceptionString = exception.toString();
    assertNotNull(exceptionString, "Exception toString should not be null");
    assertTrue(
        exceptionString.contains("DnsException"), "Exception toString should contain class name");
  }

  /** Test creating DNS exceptions for all types to ensure no runtime errors. */
  @Test
  void testCreateExceptionsForAllTypes() {
    for (DnsException.TypeEnum type : DnsException.TypeEnum.values()) {
      // Test with message only
      assertDoesNotThrow(
          () -> new DnsException(type, "Test message for " + type.getDesc()),
          "Should be able to create DNS exception with message for type: " + type);

      // Test with cause only
      RuntimeException cause = new RuntimeException("Test DNS cause");
      assertDoesNotThrow(
          () -> new DnsException(type, cause),
          "Should be able to create DNS exception with cause for type: " + type);

      // Test with message and cause
      assertDoesNotThrow(
          () -> new DnsException(type, "Test DNS message", cause),
          "Should be able to create DNS exception with message and cause for type: " + type);
    }
  }

  /** Test DNS exception message format with type description. */
  @Test
  void testExceptionMessageFormat() {
    String customMessage = "Custom error details";
    DnsException exception =
        new DnsException(DnsException.TypeEnum.INVALID_SIGNATURE, customMessage);

    String expectedMessage =
        DnsException.TypeEnum.INVALID_SIGNATURE.getDesc() + ", " + customMessage;
    assertEquals(
        expectedMessage,
        exception.getMessage(),
        "Exception message should follow 'type_desc, custom_message' format");
  }

  /** Test DNS exception categories. */
  @Test
  void testExceptionCategories() {
    // Resolver/sync errors (0-5)
    assertTrue(
        DnsException.TypeEnum.LOOK_UP_ROOT_FAILED.getValue() <= 5,
        "LOOK_UP_ROOT_FAILED should be in resolver category");
    assertTrue(
        DnsException.TypeEnum.NO_ROOT_FOUND.getValue() <= 5,
        "NO_ROOT_FOUND should be in resolver category");
    assertTrue(
        DnsException.TypeEnum.LINK_IN_NODES_TREE.getValue() <= 5,
        "LINK_IN_NODES_TREE should be in resolver category");

    // Entry parse errors (6-13)
    assertTrue(
        DnsException.TypeEnum.UNKNOWN_ENTRY.getValue() >= 6
            && DnsException.TypeEnum.UNKNOWN_ENTRY.getValue() <= 13,
        "UNKNOWN_ENTRY should be in parse category");
    assertTrue(
        DnsException.TypeEnum.INVALID_SCHEME_URL.getValue() >= 6
            && DnsException.TypeEnum.INVALID_SCHEME_URL.getValue() <= 13,
        "INVALID_SCHEME_URL should be in parse category");

    // Publish errors (14+)
    assertTrue(
        DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED.getValue() >= 14,
        "DEPLOY_DOMAIN_FAILED should be in publish category");
    assertTrue(
        DnsException.TypeEnum.OTHER_ERROR.getValue() >= 14,
        "OTHER_ERROR should be in publish category");
  }
}
