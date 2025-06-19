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
package io.xdag.p2p.example.message;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/** Message types for P2P communication examples */
@Getter
public enum MessageTypes {
  FIRST((byte) 0x00),
  TEST((byte) 0x01),
  LAST((byte) 0x8f);

  private final byte type;
  private static final Map<Byte, MessageTypes> TYPE_MAP = new HashMap<>();

  static {
    for (MessageTypes value : values()) {
      TYPE_MAP.put(value.type, value);
    }
  }

  MessageTypes(byte type) {
    this.type = type;
  }

  /**
   * Get MessageType from byte value
   *
   * @param type byte value
   * @return MessageType or null if not found
   */
  public static MessageTypes fromByte(byte type) {
    return TYPE_MAP.get(type);
  }
}
