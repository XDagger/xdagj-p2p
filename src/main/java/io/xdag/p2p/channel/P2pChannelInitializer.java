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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.p2p.config.P2pConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class P2pChannelInitializer extends ChannelInitializer<NioSocketChannel> {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;

  private final String remoteId;

  private final boolean
      peerDiscoveryMode; // only be true when channel is activated by detect service

  private final boolean trigger;

  public P2pChannelInitializer(
      P2pConfig p2pConfig,
      ChannelManager channelManager,
      String remoteId,
      boolean peerDiscoveryMode,
      boolean trigger) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;

    this.remoteId = remoteId;
    this.peerDiscoveryMode = peerDiscoveryMode;
    this.trigger = trigger;
  }

  @Override
  public void initChannel(NioSocketChannel ch) {
    try {
      final Channel channel = new Channel(p2pConfig, channelManager);
      channel.init(ch.pipeline(), remoteId, peerDiscoveryMode);

      // Optimize network buffer sizes for better performance
      ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(4 * 1024 * 1024)); // 4MB receive buffer
      ch.config().setOption(ChannelOption.SO_RCVBUF, 4 * 1024 * 1024); // 4MB socket receive buffer
      ch.config().setOption(ChannelOption.SO_SNDBUF, 4 * 1024 * 1024); // 4MB socket send buffer
      ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

      // be aware of channel closing
      ch.closeFuture()
          .addListener(
              (ChannelFutureListener)
                  future -> {
                    channel.setDisconnect(true);
                    if (channel.isDiscoveryMode()) {
                      channelManager.getNodeDetectHandler().notifyDisconnect(channel);
                    } else {
                      try {
                        log.info("Close channel:{}", channel.getInetSocketAddress());
                        channelManager.notifyDisconnect(channel);
                      } finally {
                        if (channel.getInetSocketAddress() != null
                            && channel.isActive()
                            && trigger) {
                          channelManager.triggerConnect(channel.getInetSocketAddress());
                        }
                      }
                    }
                  });

    } catch (Exception e) {
      log.error("Unexpected initChannel error", e);
    }
  }
}
