package io.xdag.p2p.handler.node;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.message.node.Message;

public interface MessageHandler {

  void onMessage(Channel channel, Message message);

  void onConnect(Channel channel);

  void onDisconnect(Channel channel);
}
