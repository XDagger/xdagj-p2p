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
            new BasicThreadFactory.Builder().namingPattern("peerBoss").build(),
            NioIoHandler.newFactory());
    // if threads = 0, it is number of core * 2
    EventLoopGroup workerGroup =
        new MultiThreadIoEventLoopGroup(
            P2pConstant.TCP_NETTY_WORK_THREAD_NUM,
            new BasicThreadFactory.Builder().namingPattern("peerWorker-%d").build(),
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
