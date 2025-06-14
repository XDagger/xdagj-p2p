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
