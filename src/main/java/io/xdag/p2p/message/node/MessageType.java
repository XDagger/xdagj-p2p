package io.xdag.p2p.message.node;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Message types for P2P node communication protocol. Handles messages between connected peers for
 * maintenance and data exchange.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Getter
public enum MessageType {
  /** Ping message for connection keep-alive */
  PING((byte) 0xFF),
  /** Pong response to ping message */
  PONG((byte) 0xFE),
  /** Handshake message for connection establishment */
  HANDSHAKE_HELLO((byte) 0xFD),
  /** Status message for peer information exchange */
  STATUS((byte) 0xFC),
  /** Disconnect message for graceful connection termination */
  DISCONNECT((byte) 0xFB),
  /** Unknown message type */
  UNKNOWN((byte) 0x80);

  private final byte type;
  private static final Map<Byte, MessageType> TYPE_MAP = new HashMap<>();

  static {
    for (MessageType messageType : values()) {
      TYPE_MAP.put(messageType.type, messageType);
    }
  }

  MessageType(byte type) {
    this.type = type;
  }

  /**
   * Get node message type from byte value.
   *
   * @param type the byte value
   * @return corresponding NodeMessageType or UNKNOWN if not found
   */
  public static MessageType fromByte(byte type) {
    return TYPE_MAP.getOrDefault(type, UNKNOWN);
  }
}
