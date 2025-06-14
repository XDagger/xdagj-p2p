package io.xdag.p2p.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class PeerClient {
  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;
  private EventLoopGroup workerGroup;

  public PeerClient(P2pConfig p2pConfig, ChannelManager channelManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
  }

  public void init() {
    workerGroup =
        new MultiThreadIoEventLoopGroup(
            0,
            new BasicThreadFactory.Builder().namingPattern("peerClient-%d").build(),
            NioIoHandler.newFactory());
  }

  public void close() {
    workerGroup.shutdownGracefully();
    workerGroup.terminationFuture().syncUninterruptibly();
  }

  public void connect(String host, int port, String remoteId) {
    try {
      ChannelFuture f = connectAsync(host, port, remoteId, false, false);
      if (f != null) {
        f.sync().channel().closeFuture().sync();
      }
    } catch (Exception e) {
      log.warn("PeerClient can't connect to {}:{} ({})", host, port, e.getMessage());
    }
  }

  public ChannelFuture connect(Node node, ChannelFutureListener future) {
    ChannelFuture channelFuture =
        connectAsync(
            node.getPreferInetSocketAddress().getAddress().getHostAddress(),
            node.getPort(),
            node.getId() == null ? BytesUtils.toHexString(NetUtils.getNodeId()) : node.getHexId(),
            false,
            false);
    if (channelManager.isShutdown) {
      return null;
    }
    if (channelFuture != null && future != null) {
      channelFuture.addListener(future);
    }
    return channelFuture;
  }

  public ChannelFuture connectAsync(Node node, boolean discoveryMode) {
    ChannelFuture channelFuture =
        connectAsync(
            node.getPreferInetSocketAddress().getAddress().getHostAddress(),
            node.getPort(),
            node.getId() == null ? BytesUtils.toHexString(NetUtils.getNodeId()) : node.getHexId(),
            discoveryMode,
            true);
    if (channelManager.isShutdown) {
      return null;
    }
    if (channelFuture != null) {
      channelFuture.addListener(
          (ChannelFutureListener)
              future -> {
                if (!future.isSuccess()) {
                  log.warn(
                      "Connect to peer {} fail, cause:{}",
                      node.getPreferInetSocketAddress(),
                      future.cause().getMessage());
                  future.channel().close();
                  if (!discoveryMode) {
                    channelManager.triggerConnect(node.getPreferInetSocketAddress());
                  }
                }
              });
    }
    return channelFuture;
  }

  private ChannelFuture connectAsync(
      String host, int port, String remoteId, boolean discoveryMode, boolean trigger) {
    try {
      Bootstrap b = new Bootstrap();
      b.group(workerGroup);
      b.channel(NioSocketChannel.class);

      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, P2pConstant.NODE_CONNECTION_TIMEOUT);
      b.remoteAddress(host, port);

      b.handler(
          new P2pChannelInitializer(p2pConfig, channelManager, remoteId, discoveryMode, trigger));

      return b.connect();

    } catch (Exception e) {
      log.warn("Connect to {}:{} failed", host, port, e);
    }
    return null;
  }
}
