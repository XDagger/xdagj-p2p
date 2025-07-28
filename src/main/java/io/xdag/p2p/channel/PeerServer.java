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
package io.xdag.p2p.channel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class PeerServer {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;
  private ChannelFuture channelFuture;
  private boolean listening;

  public PeerServer(P2pConfig p2pConfig, ChannelManager channelManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
  }

  public void init() {
    int port = p2pConfig.getPort();
    if (port > 0) {
      new Thread(() -> start(port), "PeerServer").start();
    }
  }

  public void close() {
    if (listening && channelFuture != null && channelFuture.channel().isOpen()) {
      try {
        log.info("Closing TCP server...");
        channelFuture.channel().close().sync();
      } catch (Exception e) {
        log.warn("Closing TCP server failed.", e);
      }
    }
  }

  public void start(int port) {
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
    P2pChannelInitializer p2pChannelInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "", false, true);
    try {
      ServerBootstrap b = new ServerBootstrap();

      b.group(bossGroup, workerGroup);
      b.channel(NioServerSocketChannel.class);

      b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, P2pConstant.NODE_CONNECTION_TIMEOUT);

      b.handler(new LoggingHandler());
      b.childHandler(p2pChannelInitializer);

      // Start the client.
      log.info("TCP listener started, bind port {}", port);

      channelFuture = b.bind(port).sync();

      listening = true;

      // Wait until the connection is closed.
      channelFuture.channel().closeFuture().sync();

      log.info("TCP listener closed");

    } catch (Exception e) {
      log.error("Start TCP server failed", e);
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      listening = false;
    }
  }
}
