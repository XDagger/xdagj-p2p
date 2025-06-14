package io.xdag.p2p.handler.discover;

import io.xdag.p2p.message.discover.Message;
import java.net.InetSocketAddress;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UdpEvent {
  private Message message;
  // when receive UdpEvent, this is sender address
  // when send UdpEvent, this is target address
  private InetSocketAddress address;

  public UdpEvent() {}

  public UdpEvent(Message message, InetSocketAddress address) {
    this.message = message;
    this.address = address;
  }
}
