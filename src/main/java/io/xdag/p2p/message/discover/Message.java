package io.xdag.p2p.message.discover;

import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.discover.kad.FindNodeMessage;
import io.xdag.p2p.message.discover.kad.NeighborsMessage;
import io.xdag.p2p.message.discover.kad.PingMessage;
import io.xdag.p2p.message.discover.kad.PongMessage;
import io.xdag.p2p.utils.BytesUtils;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

/**
 * Base class for node discovery messages. Handles Kademlia DHT messages like KAD_PING, KAD_PONG,
 * KAD_FIND_NODE, KAD_NEIGHBORS.
 *
 * @author XDAG Team
 * @since 0.1
 */
@Getter
public abstract class Message {
  protected P2pConfig p2pConfig;
  protected MessageType type;
  protected Bytes data;

  protected Message(P2pConfig p2pConfig, MessageType type, Bytes data) {
    this.p2pConfig = p2pConfig;
    this.type = type;
    this.data = data;
  }

  public Bytes getSendData() {
    return BytesUtils.concat(Bytes.of(type.getType()), this.data);
  }

  public abstract boolean valid();

  public static Message parse(P2pConfig p2pConfig, Bytes encode) throws Exception {
    byte type = encode.get(0);
    Bytes data = encode.slice(1);

    Message message =
        switch (MessageType.fromByte(type)) {
          case KAD_PING -> new PingMessage(p2pConfig, data);
          case KAD_PONG -> new PongMessage(p2pConfig, data);
          case KAD_FIND_NODE -> new FindNodeMessage(p2pConfig, data);
          case KAD_NEIGHBORS -> new NeighborsMessage(p2pConfig, data);
          default -> throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, "type=" + type);
        };
    if (!message.valid()) {
      throw new P2pException(P2pException.TypeEnum.BAD_MESSAGE, "type=" + type);
    }
    return message;
  }

  @Override
  public String toString() {
    return "[Message Type: " + getType() + ", len: " + (data == null ? 0 : data.size()) + "]";
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }
}
