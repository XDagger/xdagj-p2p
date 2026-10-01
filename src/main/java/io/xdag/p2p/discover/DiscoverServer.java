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
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.handler.discover.EventHandler;
import io.xdag.p2p.handler.discover.MessageHandler;
import io.xdag.p2p.handler.discover.P2pPacketDecoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class DiscoverServer {

  private final P2pConfig p2pConfig;
  private volatile Channel channel;
  private EventHandler eventHandler;

  private static final int SERVER_RESTART_WAIT = 5000;
  private static final int SERVER_CLOSE_WAIT = 10;
  /** How long {@link #init} waits for the socket to be bound (a matter of milliseconds unless the machine is busy). */
  private static final long BIND_WAIT_MS = 10_000;
  private final int port;
  private volatile boolean shutdown = false;

  public DiscoverServer(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    this.port = p2pConfig.getPort();
  }

  /**
   * Starts the discovery socket and returns when it is bound (or when it is clear that it cannot be: that is
   * logged, and the node goes on without discovery). It used to return at once, with the socket still coming
   * up on its own thread - and {@link #close()} called early left it open for good.
   */
  public void init(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
    CountDownLatch bound = new CountDownLatch(1);
    new Thread(
            () -> {
              try {
                start(bound);
              } catch (Exception e) {
                log.error("Discovery server start failed", e);
              } finally {
                bound.countDown();
              }
            },
            "DiscoverServer")
        .start();
    try {
      if (!bound.await(BIND_WAIT_MS, TimeUnit.MILLISECONDS)) {
        log.warn("Discovery socket on port {} is not bound after {} ms, going on without waiting for it", port, BIND_WAIT_MS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Whether the discovery socket is bound. */
  public boolean isListening() {
    Channel c = channel;
    return c != null && c.isActive();
  }

  public void close() {
    log.info("Closing discovery server...");
    // (also for a socket that is being bound at this moment: it closes as soon as it is)
    shutdown = true;
    Channel c = channel;
    if (c != null) {
      try {
        c.close().await(SERVER_CLOSE_WAIT, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Closing discovery server failed", e);
      }
    }
  }

  /** @param bound counted down when the socket is bound for the first time */
  private void start(CountDownLatch bound) throws Exception {
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
                    // Use custom UDP message codec only; no protobuf length framing for discovery
                    ch.pipeline().addLast(new P2pPacketDecoder(p2pConfig));
                    MessageHandler messageHandler = new MessageHandler(ch, eventHandler, p2pConfig);
                    eventHandler.setMessageSender(messageHandler);
                    ch.pipeline().addLast(messageHandler);
                  }
                });

        String bindIp = p2pConfig.getBindIp();
        Channel bind = (bindIp == null || bindIp.isBlank() ? b.bind(port) : b.bind(bindIp, port)).sync().channel();
        channel = bind;

        log.info("Discovery server started, bind port {}", port);
        // Note: channelActivated() is called automatically by Netty via MessageHandler.channelActive()
        // No explicit call needed here
        if (shutdown) {
          bind.close();
        }
        bound.countDown();

        bind.closeFuture().sync();
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
      // (the caller of init() is not kept waiting while the event loop winds down)
      bound.countDown();
      group.shutdownGracefully().sync();
    }
  }
}
