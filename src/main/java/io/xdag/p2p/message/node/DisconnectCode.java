package io.xdag.p2p.message.node;

import lombok.Getter;

/**
 * Enumeration of disconnect codes used internally for connection management. These codes are mapped
 * to standard P2P disconnect reasons when communicating with peers.
 */
@Getter
public enum DisconnectCode {
  /** Normal disconnection (no error) */
  NORMAL(0),

  /** Too many peer connections */
  TOO_MANY_PEERS(1),

  /** Different protocol version */
  DIFFERENT_VERSION(2),

  /** Node is temporarily banned */
  TIME_BANNED(3),

  /** Duplicate peer connection detected */
  DUPLICATE_PEER(4),

  /** Maximum connections from same IP reached */
  MAX_CONNECTION_WITH_SAME_IP(5),

  /** Unknown disconnection reason */
  UNKNOWN(256);

  private final Integer value;

  /**
   * Constructor for disconnect code enum.
   *
   * @param value the numeric value of the disconnect code
   */
  DisconnectCode(Integer value) {
    this.value = value;
  }

  /**
   * Find a disconnect code by its numeric value.
   *
   * @param code the numeric code to search for
   * @return the corresponding DisconnectCode, or UNKNOWN if not found
   */
  public static DisconnectCode forNumber(int code) {
    for (DisconnectCode disconnectCode : values()) {
      if (disconnectCode.value == code) {
        return disconnectCode;
      }
    }
    return UNKNOWN;
  }
}
