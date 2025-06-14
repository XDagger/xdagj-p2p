package io.xdag.p2p;

import lombok.Getter;

/**
 * Exception class for P2P communication errors. Provides typed exceptions with specific error codes
 * for different failure scenarios.
 */
@Getter
public class P2pException extends Exception {

  private final TypeEnum type;

  /**
   * Constructor with error type and message.
   *
   * @param type the type of P2P error
   * @param errMsg the error message
   */
  public P2pException(TypeEnum type, String errMsg) {
    super(errMsg);
    this.type = type;
  }

  /**
   * Constructor with error type and throwable cause.
   *
   * @param type the type of P2P error
   * @param throwable the underlying cause
   */
  public P2pException(TypeEnum type, Throwable throwable) {
    super(throwable);
    this.type = type;
  }

  /**
   * Constructor with error type, message and throwable cause.
   *
   * @param type the type of P2P error
   * @param errMsg the error message
   * @param throwable the underlying cause
   */
  public P2pException(TypeEnum type, String errMsg, Throwable throwable) {
    super(errMsg, throwable);
    this.type = type;
  }

  /**
   * Enumeration of P2P exception types. Each type represents a specific category of P2P
   * communication error.
   */
  @Getter
  public enum TypeEnum {
    /** No message handler found for the given message type */
    NO_SUCH_MESSAGE(1, "no such message"),

    /** Failed to parse received message */
    PARSE_MESSAGE_FAILED(2, "parse message failed"),

    /** Message has incorrect length */
    MESSAGE_WITH_WRONG_LENGTH(3, "message with wrong length"),

    /** Message is malformed or invalid */
    BAD_MESSAGE(4, "bad message"),

    /** Unsupported or invalid protocol */
    BAD_PROTOCOL(5, "bad protocol"),

    /** Message type is already registered */
    TYPE_ALREADY_REGISTERED(6, "type already registered"),

    /** Received empty message */
    EMPTY_MESSAGE(7, "empty message"),

    /** Message exceeds maximum allowed size */
    BIG_MESSAGE(8, "big message");

    private final Integer value;
    private final String desc;

    /**
     * Constructor for exception type enum.
     *
     * @param value the numeric value of the exception type
     * @param desc the description of the exception type
     */
    TypeEnum(Integer value, String desc) {
      this.value = value;
      this.desc = desc;
    }

    @Override
    public String toString() {
      return value + ", " + desc;
    }
  }
}
