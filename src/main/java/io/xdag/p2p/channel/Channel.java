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

import com.google.common.base.Throwables;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageQueue;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.handler.node.XdagBusinessHandler;
import io.xdag.p2p.stats.TrafficStats;
import io.xdag.p2p.stats.LayeredStats;
import io.xdag.p2p.utils.BytesUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a P2P communication channel between nodes. Handles message sending, receiving, and
 * connection management.
 */
@Getter
@Setter
@Slf4j(topic = "net")
public class Channel {

  private P2pConfig p2pConfig;

  private ChannelManager channelManager;

  /** Flag indicating if a channel is waiting for a pong response */
  public volatile boolean waitForPong = false;

  /** Timestamp when the last ping was sent */
  public volatile long pingSent = System.currentTimeMillis();

  /** Handshake message received from peer */
  private HelloMessage handshakeMessage;

  /** Node information for the connected peer */
  private Node node;

  /** Protocol version used by this channel */
  private int version;

  /** Netty channel handler context */
  private ChannelHandlerContext ctx;

  /** Remote socket address of the connected peer */
  private InetSocketAddress inetSocketAddress;

  /** IP address of the connected peer */
  private InetAddress inetAddress;

  /** Timestamp when the channel was disconnected */
  private volatile long disconnectTime;

  /** Flag indicating if the channel is disconnected */
  private volatile boolean isDisconnect = false;

  /** Timestamp of the last message sent through this channel */
  private long lastSendTime = System.currentTimeMillis();

  /** Timestamp when this channel was created */
  private final long startTime = System.currentTimeMillis();

  /** Flag indicating if the channel is active and ready for communication */
  private boolean isActive = false;

  /** Flag indicating if the connected peer is trusted */
  private boolean isTrustPeer;

  /** Flag indicating if the handshake process has completed */
  private volatile boolean finishHandshake;

  /** Unique identifier for the connected node */
  private String nodeId;

  /** Flag indicating if this channel is in discovery mode */
  private boolean discoveryMode;

  /** Average latency for this channel in milliseconds */
  private long avgLatency;

  /** Count of ping messages for latency calculation */
  private long count;

  /** Message queue for batching outgoing messages */
  private MessageQueue messageQueue;

  /** Layered statistics tracker for network and application layer metrics */
  private LayeredStats layeredStats;

  /**
   * Default constructor for Channel. Initializes a new P2P communication channel with default
   * values.
   */
  public Channel( ChannelManager channelManager) {
    this.channelManager = channelManager;
    this.layeredStats = new LayeredStats();
  }

  /**
   * Initialize the channel with pipeline handlers.
   *
   * @param pipeline the Netty channel pipeline
   * @param nodeId the node identifier
   * @param discoveryMode whether this channel is in discovery mode
   */
  public void init(ChannelPipeline pipeline, String nodeId, boolean discoveryMode) {
    this.discoveryMode = discoveryMode;
    this.nodeId = nodeId;
    this.isActive = StringUtils.isNotEmpty(nodeId);

    // Initialize message queue
    this.messageQueue = new MessageQueue(p2pConfig, this);

    pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
    pipeline.addLast(TrafficStats.getTcp());
    // Do not add protobuf length prepender; XDAG frames are raw (header + body)
    pipeline.addLast("frameCodec", new XdagFrameCodec(p2pConfig));
  }

  /**
   * Process and handle exceptions that occur in this channel.
   *
   * @param throwable the exception to process
   */
  public void processException(Throwable throwable) {
    Throwable baseThrowable = throwable;
    try {
      baseThrowable = Throwables.getRootCause(baseThrowable);
    } catch (IllegalArgumentException e) {
      baseThrowable = e.getCause();
      log.warn("Loop in causal chain detected");
    }
    SocketAddress address = ctx.channel().remoteAddress();
    if (throwable instanceof ReadTimeoutException
        || throwable instanceof IOException
        || throwable instanceof CorruptedFrameException) {
      log.warn("Close peer {}, reason: {}", address, throwable.getMessage());
    } else if (baseThrowable instanceof P2pException) {
      log.warn(
          "Close peer {}, type: ({}), info: {}",
          address,
          ((P2pException) baseThrowable).getType(),
          baseThrowable.getMessage());
    } else {
      log.error("Close peer {}, exception caught", address, throwable);
    }

    // During shutdown, don't ban nodes - just close gracefully
    if (channelManager != null && channelManager.isShutdown()) {
      closeWithoutBan();
    } else {
      close();
    }
  }

  /**
   * Set the handshake message and update related node information.
   *
   * @param handshakeMessage the handshake message from the peer
   */
  public void setHandshakeMessage(HelloMessage handshakeMessage) {
    this.handshakeMessage = handshakeMessage;
    this.node = handshakeMessage.getFrom();
    this.nodeId = node.getId();
    this.version = handshakeMessage.getVersion();
  }

  /**
   * Set the Netty channel handler context and extract connection information.
   *
   * @param ctx the Netty channel handler context
   */
  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
    this.inetAddress = inetSocketAddress.getAddress();
    this.isTrustPeer = p2pConfig.getTrustNodes().contains(inetAddress);

    // Store Channel reference in Netty context attributes for XdagFrameCodec to access
    ctx.channel().attr(XdagFrameCodec.CHANNEL_ATTRIBUTE).set(this);

    // Activate message queue after context is set
    if (messageQueue != null) {
      messageQueue.activate(ctx);
    }
  }

  /**
   * Close the channel and ban the peer for specified time.
   *
   * @param reason the reason for banning the peer
   * @param banTime time in milliseconds to ban the peer
   */
  public void close(BanReason reason, long banTime) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();

    // Deactivate message queue before closing
    if (messageQueue != null) {
      messageQueue.deactivate();
    }

    channelManager.banNode(this.inetAddress, reason, banTime);
    ctx.close();
  }

  /**
   * Close the channel with default ban reason and time.
   */
  public void close() {
    close(BanReason.PROTOCOL_VIOLATION, P2pConstant.DEFAULT_BAN_TIME);
  }

  /**
   * Close the channel without banning (for internal use, e.g., when already banned).
   * This prevents infinite recursion when ChannelManager.banNode() closes existing connections.
   */
  public void closeWithoutBan() {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();

    // Deactivate message queue before closing
    if (messageQueue != null) {
      messageQueue.deactivate();
    }

    ctx.close();
  }

  /**
   * Send a P2P message through this channel.
   *
   * @param message the P2P message to send
   */
  public void send(Message message) {
    if (message.needToLog()) {
      log.info("Send message to channel {}, {}", inetSocketAddress, message);
    } else {
      log.debug("Send message to channel {}, {}", inetSocketAddress, message);
    }
    try {
      // Use MessageQueue for batching instead of direct writeAndFlush
      if (messageQueue != null) {
        messageQueue.sendMessage(message);
      } else {
        // Fallback to direct send if queue not initialized
        ctx.channel().writeAndFlush(message);
      }
      setLastSendTime(System.currentTimeMillis());
    } catch (Exception e) {
      log.warn("Send message to {} failed, {}", inetSocketAddress, e.getMessage());
      ctx.channel().close();
    }
  }

  /**
   * Send Bytes data through this channel. This is the main implementation method for sending data.
   *
   * @param data the data to send as Tuweni Bytes
   */
  public void send(Bytes data) {
    try {
      byte type = data.get(0);
      if (isDisconnect) {
        log.warn(
            "Send to {} failed as channel has closed, message-type:{} ",
            ctx.channel().remoteAddress(),
            type);
        return;
      }

      ByteBuf byteBuf = Unpooled.wrappedBuffer(data.toArray());
      ctx.channel().writeAndFlush(byteBuf)
          .addListener(
              (ChannelFutureListener)
                  future -> {
                    if (!future.isSuccess() && !isDisconnect) {
                      log.warn(
                          "Send to {} failed, message-type:{}, cause:{}",
                          ctx.channel().remoteAddress(),
                          BytesUtils.byte2int(type),
                          future.cause().getMessage());
                    }
                  });
      setLastSendTime(System.currentTimeMillis());
    } catch (Exception e) {
      log.warn("Send message to {} failed, {}", inetSocketAddress, e.getMessage());
      ctx.channel().close();
    }
  }

  /**
   * Update the average latency for this channel.
   *
   * @param latency the new latency measurement in milliseconds
   */
  public void updateAvgLatency(long latency) {
    long total = this.avgLatency * this.count;
    this.count++;
    this.avgLatency = (total + latency) / this.count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Channel channel = (Channel) o;
    return Objects.equals(inetSocketAddress, channel.inetSocketAddress);
  }

  @Override
  public int hashCode() {
    return inetSocketAddress.hashCode();
  }

  /**
   * Get the remote address of this channel.
   *
   * @return the remote socket address
   */
  public InetSocketAddress getRemoteAddress() {
    return inetSocketAddress;
  }

  @Override
  public String toString() {
    return String.format(
        "%s | %s", inetSocketAddress, StringUtils.isEmpty(nodeId) ? "<null>" : nodeId);
  }
}
