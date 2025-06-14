package io.xdag.p2p;

import io.xdag.p2p.channel.Channel;
import java.util.Set;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

public abstract class P2pEventHandler {

  @Getter protected Set<Byte> messageTypes;

  public void onConnect(Channel channel) {}

  public void onDisconnect(Channel channel) {}

  public void onMessage(Channel channel, Bytes data) {}
}
