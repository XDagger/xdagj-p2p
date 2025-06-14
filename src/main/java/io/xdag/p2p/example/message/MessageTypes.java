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
