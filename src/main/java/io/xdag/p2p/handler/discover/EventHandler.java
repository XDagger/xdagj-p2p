package io.xdag.p2p.handler.discover;

import java.util.function.Consumer;

public interface EventHandler {

  void channelActivated();

  void handleEvent(UdpEvent event);

  void setMessageSender(Consumer<UdpEvent> messageSender);
}
