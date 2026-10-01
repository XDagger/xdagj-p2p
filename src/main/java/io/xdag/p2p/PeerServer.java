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
package io.xdag.p2p;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.channel.P2pChannelInitializer;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class PeerServer {

  /** How long {@link #start()} waits for the listener to be bound (a matter of milliseconds unless the machine is busy). */
  private static final long BIND_WAIT_MS = 10_000;

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;
  private volatile ChannelFuture channelFuture;
  private volatile boolean stopped;

  public PeerServer(P2pConfig p2pConfig, ChannelManager channelManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
  }

  /**
   * Starts the TCP listener on the configured port (none, if that is not positive) and returns when it is
   * bound: a peer that dials this node from then on is accepted. It used to return at once, with the listener
   * still coming up on its own thread - a node started right after this one was refused, and {@link #stop()}
   * called early left the listener running for good.
   *
   * <p>A port that cannot be bound is logged; the node goes on without accepting connections
   * ({@link #isListening()} tells).
   */
  public void start() {
    int port = p2pConfig.getPort();
    if (port <= 0) {
      return;
    }
    stopped = false;
    CountDownLatch bound = new CountDownLatch(1);
    new Thread(() -> serve(port, bound), "PeerServer").start();
    try {
      if (!bound.await(BIND_WAIT_MS, TimeUnit.MILLISECONDS)) {
        log.warn("TCP listener on port {} is not bound after {} ms, going on without waiting for it", port, BIND_WAIT_MS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Whether the listener is bound and accepting connections. */
  public boolean isListening() {
    ChannelFuture future = channelFuture;
    return future != null && future.channel().isActive();
  }

  public void stop() {
    // (also for a listener that is being bound at this moment: it closes as soon as it is)
    stopped = true;
    ChannelFuture future = channelFuture;
    if (future != null && future.channel().isOpen()) {
      try {
        log.info("Closing TCP server...");
        future.channel().close().sync();
      } catch (Exception e) {
        log.warn("Closing TCP server failed.", e);
      }
    }
  }

  /** Binds the listener and serves until it is closed: this call blocks. */
  public void start(int port) {
    serve(port, new CountDownLatch(1));
  }

  /** @param bound counted down when the listener is bound, or when it is clear that it will not be */
  private void serve(int port, CountDownLatch bound) {
    EventLoopGroup bossGroup =
        new MultiThreadIoEventLoopGroup(
            1,
            BasicThreadFactory.builder().namingPattern("peerBoss").build(),
            NioIoHandler.newFactory());
    // if threads = 0, it is number of cores * 2
    EventLoopGroup workerGroup =
        new MultiThreadIoEventLoopGroup(
            P2pConstant.TCP_NETTY_WORK_THREAD_NUM,
            BasicThreadFactory.builder().namingPattern("peerWorker-%d").build(),
            NioIoHandler.newFactory());
    try {
      P2pChannelInitializer p2pChannelInitializer =
          new P2pChannelInitializer(p2pConfig, channelManager, p2pConfig.getNodeKey(), false);
      ServerBootstrap b = new ServerBootstrap();

      b.group(bossGroup, workerGroup);
      b.channel(NioServerSocketChannel.class);

      b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, P2pConstant.NODE_CONNECTION_TIMEOUT);
      b.option(ChannelOption.SO_BACKLOG, 128);
      b.option(ChannelOption.SO_REUSEADDR, true);
      b.childOption(ChannelOption.SO_KEEPALIVE, true);
      b.childOption(ChannelOption.TCP_NODELAY, true);

      b.childHandler(p2pChannelInitializer);

      String bindIp = p2pConfig.getBindIp();
      ChannelFuture future = (bindIp == null || bindIp.isBlank() ? b.bind(port) : b.bind(bindIp, port)).sync();
      channelFuture = future;
      log.info("TCP listener started, bind {}:{}", bindIp == null || bindIp.isBlank() ? "*" : bindIp, port);
      if (stopped) {
        future.channel().close();
      }
      bound.countDown();

      // Wait until the connection is closed.
      future.channel().closeFuture().sync();

      log.info("TCP listener closed");

    } catch (Exception e) {
      log.error("Start TCP server failed", e);
    } finally {
      bound.countDown();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }
  }
}
