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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageQueue;
import io.xdag.p2p.Peer;
import io.xdag.p2p.stats.LayeredStats;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

  /** Message queue for batching outgoing messages */
  private MessageQueue messageQueue;

  /** Layered statistics tracker for network and application layer metrics */
  private LayeredStats layeredStats;

  /** What the peer said about itself in the handshake (null if the channel was registered without one). */
  private Peer peer;

  /**
   * Address the peer listens on: its IP address as seen by us plus the port it announced in the handshake.
   * For a peer that dialled us this is not the remote address of the connection (that port is ephemeral).
   */
  private InetSocketAddress listenAddress;

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
    // Do not add protobuf length prepender; XDAG frames are raw (header + body)
    pipeline.addLast("frameCodec", new XdagFrameCodec(p2pConfig));
  }

  /**
   * Set the Netty channel handler context and extract connection information.
   *
   * @param ctx the Netty channel handler context
   */
  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
    this.inetAddress = inetSocketAddress == null ? null : inetSocketAddress.getAddress();
    this.isTrustPeer = p2pConfig != null && inetAddress != null && p2pConfig.getTrustNodes().contains(inetAddress);

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
   * @param banTime time in milliseconds to ban the peer
   */
  public void close(long banTime) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();

    // Deactivate message queue before closing
    if (messageQueue != null) {
      messageQueue.deactivate();
    }

    if (channelManager != null) {
      channelManager.banNode(this.inetAddress, banTime);
    }
    if (ctx != null) {
      ctx.close();
    }
  }

  /**
   * Close the channel with default ban time.
   */
  public void close() {
    close(P2pConstant.DEFAULT_BAN_TIME);
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

    if (ctx != null) {
      ctx.close();
    }
  }

  /**
   * Whether more data can be queued for this peer right now. False while the peer reads more slowly than we
   * write (the socket buffer and Netty's outbound buffer are above the high-water mark). A sender of bulk data
   * should stop here and continue on {@link io.xdag.p2p.P2pEventHandler#onWritabilityChanged}.
   */
  public boolean isWritable() {
    return !isDisconnect && ctx != null && ctx.channel().isActive() && ctx.channel().isWritable();
  }

  /** Bytes queued for this peer that the socket has not taken yet. */
  public long pendingOutboundBytes() {
    if (ctx == null || ctx.channel() == null || ctx.channel().unsafe() == null) {
      return 0;
    }
    io.netty.channel.ChannelOutboundBuffer buffer = ctx.channel().unsafe().outboundBuffer();
    return buffer == null ? 0 : buffer.totalPendingWriteBytes();
  }

  /**
   * A peer that does not read must not make us buffer without bound: once more than
   * {@code maxOutboundQueueBytes} are waiting for it the connection is dropped.
   *
   * @return true if the connection was dropped
   */
  private boolean dropIfNotReading() {
    if (p2pConfig != null && ctx != null && pendingOutboundBytes() > p2pConfig.getMaxOutboundQueueBytes()) {
      log.debug("Peer {} does not read ({} bytes queued), disconnecting", inetSocketAddress, pendingOutboundBytes());
      closeWithoutBan();
      return true;
    }
    return false;
  }

  /**
   * Send a P2P message through this channel.
   *
   * @param message the P2P message to send
   */
  public void send(Message message) {
    log.trace("Send message to channel {}, {}", inetSocketAddress, message);
    if (isDisconnect || ctx == null || dropIfNotReading()) {
      return;
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
   * Send an application message given as {@code [code | body]} - the form in which
   * {@link io.xdag.p2p.P2pEventHandler#onMessage} delivers messages. The code must be an application code
   * (0x16 and above); the framework's own messages cannot be sent this way. The bytes are framed, split and
   * compressed like any message. (They used to be written to the socket as they were, without a frame, which
   * the other side could not read.)
   *
   * @param data the message to send as Tuweni Bytes
   */
  public void send(Bytes data) {
    if (data == null || data.isEmpty()) {
      return;
    }
    byte type = data.get(0);
    if ((0xFF & type) < 0x16) {
      throw new IllegalArgumentException("not an application message code: " + type);
    }
    send(new ApplicationMessage(type, data.slice(1).toArray()));
  }

  /** An application message: a code and a body that the framework does not look into. */
  static final class ApplicationMessage extends Message {
    ApplicationMessage(byte code, byte[] body) {
      super(() -> code, null);
      this.body = body;
    }

    @Override
    public void encode(io.xdag.p2p.utils.SimpleEncoder enc) {
      enc.writeBytes(body);
    }

    @Override
    public String toString() {
      return String.format("ApplicationMessage{code=0x%02X, bodyLen=%d}", code.toByte(), body == null ? 0 : body.length);
    }
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
    return Objects.hashCode(inetSocketAddress);
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
