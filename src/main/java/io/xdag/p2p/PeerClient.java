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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.channel.P2pChannelInitializer;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
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

    public void start() {
        workerGroup =
                new MultiThreadIoEventLoopGroup(
                        0,
                        BasicThreadFactory.builder().namingPattern("peerClient-%d").build(),
                        NioIoHandler.newFactory());
    }

    public void stop() {
        workerGroup.shutdownGracefully();
        workerGroup.terminationFuture().syncUninterruptibly();
    }

    public void connect(String host, int port) {
        try {
            ChannelFuture f = connectAsync(host, port);
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
                        node.getPort());
        if (channelManager.isShutdown()) {
            return null;
        }
        if (channelFuture != null && future != null) {
            channelFuture.addListener(future);
        }
        return channelFuture;
    }

    public ChannelFuture connectAsync(Node node) {
        ChannelFuture channelFuture =
                connectAsync(
                        node.getPreferInetSocketAddress().getAddress().getHostAddress(),
                        node.getPort());
        if (channelManager.isShutdown()) {
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
                                }
                            });
        }
        return channelFuture;
    }

private ChannelFuture connectAsync(String host, int port) {
    try {
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, P2pConstant.NODE_CONNECTION_TIMEOUT);
        b.remoteAddress(host, port);
        b.handler(new P2pChannelInitializer(p2pConfig, channelManager, p2pConfig.getNodeKey(), true));
        return b.connect();
    } catch (Exception e) {
        log.warn("Connect to {}:{} failed", host, port, e);
    }
    return null;
}
}
