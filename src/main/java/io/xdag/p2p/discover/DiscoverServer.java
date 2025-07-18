/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.discover;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.handler.discover.EventHandler;
import io.xdag.p2p.handler.discover.MessageHandler;
import io.xdag.p2p.handler.discover.P2pPacketDecoder;
import io.xdag.p2p.stats.TrafficStats;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class DiscoverServer {

  private final P2pConfig p2pConfig;
  private Channel channel;
  private EventHandler eventHandler;

  private static final int SERVER_RESTART_WAIT = 5000;
  private static final int SERVER_CLOSE_WAIT = 10;
  private final int port;
  private volatile boolean shutdown = false;

  public DiscoverServer(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    this.port = p2pConfig.getPort();
  }

  public void init(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
    new Thread(
            () -> {
              try {
                start();
              } catch (Exception e) {
                log.error("Discovery server start failed", e);
              }
            },
            "DiscoverServer")
        .start();
  }

  public void close() {
    log.info("Closing discovery server...");
    shutdown = true;
    if (channel != null) {
      try {
        channel.close().await(SERVER_CLOSE_WAIT, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Closing discovery server failed", e);
      }
    }
  }

  private void start() throws Exception {
    MultiThreadIoEventLoopGroup group =
        new MultiThreadIoEventLoopGroup(
            P2pConstant.UDP_NETTY_WORK_THREAD_NUM,
            BasicThreadFactory.builder().namingPattern("discoverServer").build(),
            NioIoHandler.newFactory());
    try {
      while (!shutdown) {
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioDatagramChannel.class)
            .handler(
                new ChannelInitializer<NioDatagramChannel>() {
                  @Override
                  public void initChannel(NioDatagramChannel ch) {
                    ch.pipeline().addLast(TrafficStats.getUdp());
                    ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                    ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                    ch.pipeline().addLast(new P2pPacketDecoder(p2pConfig));
                    MessageHandler messageHandler = new MessageHandler(ch, eventHandler);
                    eventHandler.setMessageSender(messageHandler);
                    ch.pipeline().addLast(messageHandler);
                  }
                });

        channel = b.bind(port).sync().channel();

        log.info("Discovery server started, bind port {}", port);

        channel.closeFuture().sync();
        if (shutdown) {
          log.info("Shutdown discovery server");
          break;
        }
        log.warn("Restart discovery server after 5 sec pause...");
        Thread.sleep(SERVER_RESTART_WAIT);
      }
    } catch (InterruptedException e) {
      log.warn("Discover server interrupted");
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Start discovery server with port {} failed", port, e);
    } finally {
      group.shutdownGracefully().sync();
    }
  }
}
