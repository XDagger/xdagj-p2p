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
package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for P2pException class. Tests exception creation, message handling, type enumeration,
 * and exception chaining.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Slf4j(topic = "test")
public class P2pExceptionTest {

  /** Test P2pException creation with type and message. */
  @Test
  void testExceptionWithTypeAndMessage() {
    String errorMessage = "Test error message";
    P2pException exception = new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, errorMessage);

    assertEquals(
        P2pException.TypeEnum.NO_SUCH_MESSAGE, exception.getType(), "Exception type should match");
    assertEquals(errorMessage, exception.getMessage(), "Exception message should match");
    assertNull(exception.getCause(), "Exception should have no cause");
  }

  /** Test P2pException creation with type and throwable cause. */
  @Test
  void testExceptionWithTypeAndCause() {
    RuntimeException cause = new RuntimeException("Root cause");
    P2pException exception = new P2pException(P2pException.TypeEnum.PARSE_MESSAGE_FAILED, cause);

    assertEquals(
        P2pException.TypeEnum.PARSE_MESSAGE_FAILED,
        exception.getType(),
        "Exception type should match");
    assertEquals(cause, exception.getCause(), "Exception cause should match");
    assertEquals(
        "java.lang.RuntimeException: Root cause",
        exception.getMessage(),
        "Exception message should include cause");
  }

  /** Test P2pException creation with type, message and throwable cause. */
  @Test
  void testExceptionWithTypeMessageAndCause() {
    String errorMessage = "Custom error message";
    RuntimeException cause = new RuntimeException("Root cause");
    P2pException exception =
        new P2pException(P2pException.TypeEnum.MESSAGE_WITH_WRONG_LENGTH, errorMessage, cause);

    assertEquals(
        P2pException.TypeEnum.MESSAGE_WITH_WRONG_LENGTH,
        exception.getType(),
        "Exception type should match");
    assertEquals(errorMessage, exception.getMessage(), "Exception message should match");
    assertEquals(cause, exception.getCause(), "Exception cause should match");
  }

  /** Test all P2pException type enums. */
  @Test
  void testAllExceptionTypes() {
    // Test each exception type
    P2pException.TypeEnum[] types = P2pException.TypeEnum.values();
    assertTrue(types.length > 0, "Should have at least one exception type");

    for (P2pException.TypeEnum type : types) {
      assertNotNull(type.getValue(), "Type value should not be null");
      assertNotNull(type.getDesc(), "Type description should not be null");
      assertFalse(type.getDesc().isEmpty(), "Type description should not be empty");

      // Test toString format
      String toString = type.toString();
      assertTrue(toString.contains(type.getValue().toString()), "toString should contain value");
      assertTrue(toString.contains(type.getDesc()), "toString should contain description");
    }
  }

  /** Test specific exception types and their properties. */
  @Test
  void testSpecificExceptionTypes() {
    // Test NO_SUCH_MESSAGE
    assertEquals(
        1, P2pException.TypeEnum.NO_SUCH_MESSAGE.getValue(), "NO_SUCH_MESSAGE should have value 1");
    assertEquals(
        "no such message",
        P2pException.TypeEnum.NO_SUCH_MESSAGE.getDesc(),
        "NO_SUCH_MESSAGE should have correct description");

    // Test PARSE_MESSAGE_FAILED
    assertEquals(
        2,
        P2pException.TypeEnum.PARSE_MESSAGE_FAILED.getValue(),
        "PARSE_MESSAGE_FAILED should have value 2");
    assertEquals(
        "parse message failed",
        P2pException.TypeEnum.PARSE_MESSAGE_FAILED.getDesc(),
        "PARSE_MESSAGE_FAILED should have correct description");

    // Test MESSAGE_WITH_WRONG_LENGTH
    assertEquals(
        3,
        P2pException.TypeEnum.MESSAGE_WITH_WRONG_LENGTH.getValue(),
        "MESSAGE_WITH_WRONG_LENGTH should have value 3");
    assertEquals(
        "message with wrong length",
        P2pException.TypeEnum.MESSAGE_WITH_WRONG_LENGTH.getDesc(),
        "MESSAGE_WITH_WRONG_LENGTH should have correct description");

    // Test BAD_MESSAGE
    assertEquals(
        4, P2pException.TypeEnum.BAD_MESSAGE.getValue(), "BAD_MESSAGE should have value 4");
    assertEquals(
        "bad message",
        P2pException.TypeEnum.BAD_MESSAGE.getDesc(),
        "BAD_MESSAGE should have correct description");

    // Test BAD_PROTOCOL
    assertEquals(
        5, P2pException.TypeEnum.BAD_PROTOCOL.getValue(), "BAD_PROTOCOL should have value 5");
    assertEquals(
        "bad protocol",
        P2pException.TypeEnum.BAD_PROTOCOL.getDesc(),
        "BAD_PROTOCOL should have correct description");

    // Test TYPE_ALREADY_REGISTERED
    assertEquals(
        6,
        P2pException.TypeEnum.TYPE_ALREADY_REGISTERED.getValue(),
        "TYPE_ALREADY_REGISTERED should have value 6");
    assertEquals(
        "type already registered",
        P2pException.TypeEnum.TYPE_ALREADY_REGISTERED.getDesc(),
        "TYPE_ALREADY_REGISTERED should have correct description");

    // Test EMPTY_MESSAGE
    assertEquals(
        7, P2pException.TypeEnum.EMPTY_MESSAGE.getValue(), "EMPTY_MESSAGE should have value 7");
    assertEquals(
        "empty message",
        P2pException.TypeEnum.EMPTY_MESSAGE.getDesc(),
        "EMPTY_MESSAGE should have correct description");

    // Test BIG_MESSAGE
    assertEquals(
        8, P2pException.TypeEnum.BIG_MESSAGE.getValue(), "BIG_MESSAGE should have value 8");
    assertEquals(
        "big message",
        P2pException.TypeEnum.BIG_MESSAGE.getDesc(),
        "BIG_MESSAGE should have correct description");
  }

  /** Test exception type toString format. */
  @Test
  void testExceptionTypeToString() {
    P2pException.TypeEnum type = P2pException.TypeEnum.NO_SUCH_MESSAGE;
    String toString = type.toString();

    assertEquals(
        "1, no such message", toString, "toString should follow 'value, description' format");
  }

  /** Test exception inheritance and instanceof checks. */
  @Test
  void testExceptionInheritance() {
    P2pException exception = new P2pException(P2pException.TypeEnum.BAD_MESSAGE, "test");

    assertInstanceOf(Exception.class, exception, "P2pException should be instance of Exception");
    assertInstanceOf(Throwable.class, exception, "P2pException should be instance of Throwable");
  }

  /** Test exception with null message. */
  @Test
  void testExceptionWithNullMessage() {
    P2pException exception = new P2pException(P2pException.TypeEnum.EMPTY_MESSAGE, (String) null);

    assertEquals(
        P2pException.TypeEnum.EMPTY_MESSAGE, exception.getType(), "Exception type should match");
    assertNull(exception.getMessage(), "Exception message should be null");
  }

  /** Test exception with empty message. */
  @Test
  void testExceptionWithEmptyMessage() {
    String emptyMessage = "";
    P2pException exception = new P2pException(P2pException.TypeEnum.BIG_MESSAGE, emptyMessage);

    assertEquals(
        P2pException.TypeEnum.BIG_MESSAGE, exception.getType(), "Exception type should match");
    assertEquals(emptyMessage, exception.getMessage(), "Exception message should be empty string");
  }

  /** Test exception chaining with multiple levels. */
  @Test
  void testExceptionChaining() {
    // Create a chain of exceptions
    RuntimeException rootCause = new RuntimeException("Root cause");
    IllegalArgumentException intermediateCause =
        new IllegalArgumentException("Intermediate cause", rootCause);
    P2pException topException =
        new P2pException(
            P2pException.TypeEnum.PARSE_MESSAGE_FAILED, "Top level error", intermediateCause);

    assertEquals(
        P2pException.TypeEnum.PARSE_MESSAGE_FAILED,
        topException.getType(),
        "Top exception type should match");
    assertEquals(
        "Top level error", topException.getMessage(), "Top exception message should match");
    assertEquals(
        intermediateCause, topException.getCause(), "Top exception cause should be intermediate");
    assertEquals(
        rootCause,
        topException.getCause().getCause(),
        "Intermediate exception cause should be root");
  }

  /** Test exception serialization compatibility. */
  @Test
  void testExceptionSerialization() {
    P2pException exception =
        new P2pException(P2pException.TypeEnum.BAD_PROTOCOL, "Serialization test");

    // Basic checks that would be needed for serialization
    assertNotNull(exception.getType(), "Type should not be null for serialization");
    assertNotNull(exception.getMessage(), "Message should not be null for serialization");

    // Test that the exception can be converted to string
    String exceptionString = exception.toString();
    assertNotNull(exceptionString, "Exception toString should not be null");
    assertTrue(
        exceptionString.contains("P2pException"), "Exception toString should contain class name");
  }

  /** Test creating exceptions for all types to ensure no runtime errors. */
  @Test
  void testCreateExceptionsForAllTypes() {
    for (P2pException.TypeEnum type : P2pException.TypeEnum.values()) {
      // Test with message only
      assertDoesNotThrow(
          () -> new P2pException(type, "Test message for " + type.getDesc()),
          "Should be able to create exception with message for type: " + type);

      // Test with cause only
      RuntimeException cause = new RuntimeException("Test cause");
      assertDoesNotThrow(
          () -> new P2pException(type, cause),
          "Should be able to create exception with cause for type: " + type);

      // Test with message and cause
      assertDoesNotThrow(
          () -> new P2pException(type, "Test message", cause),
          "Should be able to create exception with message and cause for type: " + type);
    }
  }
}
