package io.xdag.p2p.handler.node;

import static io.xdag.p2p.config.P2pConstant.KEEP_ALIVE_TIMEOUT;
import static io.xdag.p2p.config.P2pConstant.PING_TIMEOUT;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class KeepAliveHandler implements MessageHandler {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;

  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          BasicThreadFactory.builder().namingPattern("keepAlive").build());

  public KeepAliveHandler(P2pConfig p2pConfig, ChannelManager channelManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
  }

  public void init() {
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            long now = System.currentTimeMillis();
            channelManager.getChannels().values().stream()
                .filter(p -> !p.isDisconnect())
                .forEach(
                    p -> {
                      if (p.waitForPong) {
                        if (now - p.pingSent > KEEP_ALIVE_TIMEOUT) {
                          p.send(
                              new P2pDisconnectMessage(p2pConfig, DisconnectReason.PING_TIMEOUT));
                          p.close();
                        }
                      } else {
                        if (now - p.getLastSendTime() > PING_TIMEOUT && p.isFinishHandshake()) {
                          p.send(new PingMessage(p2pConfig));
                          p.waitForPong = true;
                          p.pingSent = now;
                        }
                      }
                    });
          } catch (Exception t) {
            log.error("Exception in keep alive task", t);
          }
        },
        2,
        2,
        TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  @Override
  public void onMessage(Channel channel, Message message) {
    switch (message.getType()) {
      case PING:
        channel.send(new PongMessage(p2pConfig));
        break;
      case PONG:
        channel.updateAvgLatency(System.currentTimeMillis() - channel.pingSent);
        channel.waitForPong = false;
        break;
      default:
        break;
    }
  }

  @Override
  public void onConnect(Channel channel) {}

  @Override
  public void onDisconnect(Channel channel) {}
}
