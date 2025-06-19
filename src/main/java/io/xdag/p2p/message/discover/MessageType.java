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
package io.xdag.p2p.message.discover;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Message types for Kademlia discovery protocol. Handles UDP-based node discovery and DHT
 * operations.
 *
 * @author XDAG Team
 * @since 0.1
 */
@Getter
public enum MessageType {
  /** Ping message for node discovery */
  KAD_PING((byte) 0x01),
  /** Pong response to ping message */
  KAD_PONG((byte) 0x02),
  /** Find node request for DHT lookup */
  KAD_FIND_NODE((byte) 0x03),
  /** Neighbors response with found nodes */
  KAD_NEIGHBORS((byte) 0x04),
  /** Unknown message type */
  UNKNOWN((byte) 0xFF);

  private final byte type;

  MessageType(byte type) {
    this.type = type;
  }

  private static final Map<Byte, MessageType> map = new HashMap<>();

  static {
    for (MessageType value : values()) {
      map.put(value.type, value);
    }
  }

  public static MessageType fromByte(byte type) {
    MessageType typeEnum = map.get(type);
    return typeEnum == null ? UNKNOWN : typeEnum;
  }
}
