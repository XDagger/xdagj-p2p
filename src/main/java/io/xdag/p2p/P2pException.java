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
   * Constructor with the error type and throwable cause.
   *
   * @param type the type of P2P error
   * @param throwable the underlying cause
   */
  public P2pException(TypeEnum type, Throwable throwable) {
    super(throwable);
    this.type = type;
  }

  /**
   * Constructor with the error type, message and throwable cause.
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

    /** Failed to parse received the message */
    PARSE_MESSAGE_FAILED(2, "parse message failed"),

    /** Message has incorrect length */
    MESSAGE_WITH_WRONG_LENGTH(3, "message with wrong length"),

    /** The Message is malformed or invalid */
    BAD_MESSAGE(4, "bad message"),

    /** Unsupported or invalid protocol */
    BAD_PROTOCOL(5, "bad protocol"),

    /** Message type is already registered */
    TYPE_ALREADY_REGISTERED(6, "type already registered"),

    /** Received the empty message */
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
