package io.xdag.p2p.message.node;

import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.utils.BytesUtils;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

/**
 * Base class for TCP Node communication messages. Handles messages like HANDSHAKE, KEEP_ALIVE,
 * DISCONNECT, COMPRESS.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Getter
public abstract class Message {

  protected P2pConfig p2pConfig;

  /** The message type */
  protected final MessageType type;

  /** The message data payload */
  protected Bytes data;

  /**
   * Constructor for Node message.
   *
   * @param type the specific message type
   * @param data the message data payload
   */
  protected Message(P2pConfig p2pConfig, MessageType type, Bytes data) {
    this.p2pConfig = p2pConfig;
    this.type = type;
    this.data = data == null ? Bytes.EMPTY : data;
  }

  public Bytes getSendData() {
    return BytesUtils.concat(Bytes.of(type.getType()), this.data);
  }

  /**
   * Validate if this message is properly formed. Subclasses should override this method to provide
   * specific validation logic.
   *
   * @return true if the message is valid, false otherwise
   */
  public abstract boolean valid();

  /**
   * Determine if this message type should be logged.
   *
   * @return true if this message should be logged
   */
  public boolean needToLog() {
    return type.equals(MessageType.HANDSHAKE_HELLO) || type.equals(MessageType.DISCONNECT);
  }

  public static Message parse(P2pConfig p2pConfig, Bytes encode) throws P2pException {
    byte type = encode.get(0);
    try {
      Bytes data = encode.slice(1);
      Message message =
          switch (MessageType.fromByte(type)) {
            case PING -> new PingMessage(p2pConfig, data);
            case PONG -> new PongMessage(p2pConfig, data);
            case HANDSHAKE_HELLO -> new HelloMessage(p2pConfig, data);
            case STATUS -> new StatusMessage(p2pConfig, data);
            case DISCONNECT -> new P2pDisconnectMessage(p2pConfig, data);
            default ->
                throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, "type=" + type);
          };
      if (!message.valid()) {
        throw new P2pException(P2pException.TypeEnum.BAD_MESSAGE, "type=" + type);
      }
      return message;
    } catch (P2pException p2pException) {
      throw p2pException;
    } catch (Exception e) {
      throw new P2pException(P2pException.TypeEnum.BAD_MESSAGE, "type:" + type);
    }
  }

  @Override
  public String toString() {
    return "type: " + getType() + ", ";
  }
}
