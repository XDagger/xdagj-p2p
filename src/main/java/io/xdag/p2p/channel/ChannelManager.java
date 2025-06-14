package io.xdag.p2p.channel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.P2pException.TypeEnum;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsManager;
import io.xdag.p2p.handler.node.ConnPoolHandler;
import io.xdag.p2p.handler.node.HandshakeHandler;
import io.xdag.p2p.handler.node.KeepAliveHandler;
import io.xdag.p2p.handler.node.NodeDetectHandler;
import io.xdag.p2p.message.node.DisconnectCode;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

/**
 * Central manager for P2P channels and connections. Handles channel lifecycle, connection
 * management, and message routing.
 */
@Getter
@Setter
@Slf4j(topic = "net")
public class ChannelManager {

  private P2pConfig p2pConfig;

  private NodeManager nodeManager;

  private DnsManager dnsManager;

  /** Node detection handler for managing peer discovery */
  private NodeDetectHandler nodeDetectHandler;

  /** Peer server for accepting incoming connections */
  private PeerServer peerServer;

  /** Peer client for initiating outbound connections */
  private PeerClient peerClient;

  /** Connection pool handler for managing connection lifecycle */
  private ConnPoolHandler connPoolHandler;

  /** Ping-pong handler for maintaining connection health */
  private KeepAliveHandler keepAliveHandler;

  /** Handshake handler for connection establishment */
  private HandshakeHandler handshakeHandler;

  /** Map of active channels indexed by socket address */
  private final Map<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();

  /** Cache of banned nodes with ban timestamps */
  private final Cache<InetAddress, Long> bannedNodes;

  /** Flag indicating if the channel manager has been initialized */
  private boolean isInit = false;

  /** Flag indicating if the channel manager has been shutdown */
  public volatile boolean isShutdown = false;

  /** Private constructor to prevent instantiation of utility class. */
  public ChannelManager(P2pConfig p2pConfig, NodeManager nodeManager, DnsManager dnsManager) {
    this.p2pConfig = p2pConfig;
    this.nodeManager = nodeManager;
    this.dnsManager = dnsManager;
    this.bannedNodes = CacheBuilder.newBuilder().maximumSize(2000).build(); // ban timestamp
  }

  /** Initialize the channel manager and all its components. */
  public void init() {
    isInit = true;
    peerServer = new PeerServer(p2pConfig, this);
    peerClient = new PeerClient(p2pConfig, this);

    keepAliveHandler = new KeepAliveHandler(p2pConfig, this);
    connPoolHandler = new ConnPoolHandler(p2pConfig, this, nodeManager, dnsManager);
    handshakeHandler = new HandshakeHandler(p2pConfig, this);
    nodeDetectHandler = new NodeDetectHandler(p2pConfig, this, nodeManager);

    peerServer.init();
    peerClient.init();
    keepAliveHandler.init();

    connPoolHandler.init(peerClient);
    nodeDetectHandler.init(peerClient);
  }

  /**
   * Connect to a peer at the specified address.
   *
   * @param address the socket address to connect to
   */
  public void connect(InetSocketAddress address) {
    peerClient.connect(
        address.getAddress().getHostAddress(),
        address.getPort(),
        BytesUtils.toHexString(NetUtils.getNodeId()));
  }

  /**
   * Connect to a peer node with a future listener.
   *
   * @param node the target node to connect to
   * @param future the channel future listener
   * @return ChannelFuture for the connection
   */
  public ChannelFuture connect(Node node, ChannelFutureListener future) {
    return peerClient.connect(node, future);
  }

  /**
   * Notify that a channel has been disconnected and clean up resources.
   *
   * @param channel the disconnected channel
   */
  public void notifyDisconnect(Channel channel) {
    if (channel.getInetSocketAddress() == null) {
      log.warn("Notify Disconnect peer has no address.");
      return;
    }
    channels.remove(channel.getInetSocketAddress());
    p2pConfig.handlerList.forEach(h -> h.onDisconnect(channel));
    InetAddress inetAddress = channel.getInetAddress();
    if (inetAddress != null) {
      banNode(inetAddress, P2pConstant.DEFAULT_BAN_TIME);
    }
  }

  /**
   * Get the number of active connections from a specific IP address.
   *
   * @param inetAddress the IP address to check
   * @return number of active connections from this IP
   */
  public int getConnectionNum(InetAddress inetAddress) {
    int cnt = 0;
    for (Channel channel : channels.values()) {
      if (channel.getInetAddress().equals(inetAddress)) {
        cnt++;
      }
    }
    return cnt;
  }

  /**
   * Process a peer connection and determine if it should be accepted or rejected.
   *
   * @param channel the channel to process
   * @return disconnect code indicating the result
   */
  public synchronized DisconnectCode processPeer(Channel channel) {
    log.debug(
        "Processing peer: {}, isActive: {}, isTrustPeer: {}",
        channel.getInetSocketAddress(),
        channel.isActive(),
        channel.isTrustPeer());

    if (!channel.isActive() && !channel.isTrustPeer()) {
      InetAddress inetAddress = channel.getInetAddress();
      if (bannedNodes.getIfPresent(inetAddress) != null
          && bannedNodes.getIfPresent(inetAddress) > System.currentTimeMillis()) {
        log.info("Peer {} recently disconnected", channel);
        return DisconnectCode.TIME_BANNED;
      }

      log.debug("Current connections: {}, max: {}", channels.size(), p2pConfig.getMaxConnections());
      if (channels.size() >= p2pConfig.getMaxConnections()) {
        log.info("Too many peers, disconnected with {}", channel);
        return DisconnectCode.TOO_MANY_PEERS;
      }

      int num = getConnectionNum(channel.getInetAddress());
      log.debug(
          "Connections from same IP {}: {}, max: {}",
          channel.getInetAddress(),
          num,
          p2pConfig.getMaxConnectionsWithSameIp());
      if (num >= p2pConfig.getMaxConnectionsWithSameIp()) {
        log.info("Max connection with same ip {}", channel);
        return DisconnectCode.MAX_CONNECTION_WITH_SAME_IP;
      }
    }

    if (StringUtils.isNotEmpty(channel.getNodeId())) {
      for (Channel c : channels.values()) {
        if (channel.getNodeId().equals(c.getNodeId())) {
          if (c.getStartTime() > channel.getStartTime()) {
            log.debug("Closing newer duplicate channel: {}", c.getInetSocketAddress());
            c.close();
          } else {
            log.info("Duplicate peer {}, exist peer {}", channel, c);
            return DisconnectCode.DUPLICATE_PEER;
          }
        }
      }
    }

    channels.put(channel.getInetSocketAddress(), channel);

    log.info("Add peer {}, total channels: {}", channel.getInetSocketAddress(), channels.size());
    return DisconnectCode.NORMAL;
  }

  /**
   * Convert a disconnect code to a disconnect reason.
   *
   * @param code the disconnect code
   * @return corresponding disconnect reason
   */
  public DisconnectReason getDisconnectReason(DisconnectCode code) {
    DisconnectReason disconnectReason;
    switch (code) {
      case DIFFERENT_VERSION:
        disconnectReason = DisconnectReason.DIFFERENT_VERSION;
        break;
      case TIME_BANNED:
        disconnectReason = DisconnectReason.RECENT_DISCONNECT;
        break;
      case DUPLICATE_PEER:
        disconnectReason = DisconnectReason.DUPLICATE_PEER;
        break;
      case TOO_MANY_PEERS:
        disconnectReason = DisconnectReason.TOO_MANY_PEERS;
        break;
      case MAX_CONNECTION_WITH_SAME_IP:
        disconnectReason = DisconnectReason.TOO_MANY_PEERS_WITH_SAME_IP;
        break;
      default:
        {
          disconnectReason = DisconnectReason.UNKNOWN;
        }
    }
    return disconnectReason;
  }

  /**
   * Log the disconnect reason for a channel.
   *
   * @param channel the channel being disconnected
   * @param reason the reason for disconnection
   */
  public void logDisconnectReason(Channel channel, DisconnectReason reason) {
    log.info("Try to close channel: {}, reason: {}", channel.getInetSocketAddress(), reason.name());
  }

  /**
   * Ban a node for a specified time period.
   *
   * @param inetAddress the IP address to ban
   * @param banTime the ban duration in milliseconds
   */
  public void banNode(InetAddress inetAddress, Long banTime) {
    long now = System.currentTimeMillis();
    if (bannedNodes.getIfPresent(inetAddress) == null
        || bannedNodes.getIfPresent(inetAddress) < now) {
      bannedNodes.put(inetAddress, now + banTime);
    }
  }

  /** Close the channel manager and all its components. */
  public void close() {
    if (!isInit || isShutdown) {
      return;
    }
    isShutdown = true;
    connPoolHandler.close();
    keepAliveHandler.close();
    peerServer.close();
    peerClient.close();
    nodeDetectHandler.close();
  }

  /**
   * Process an incoming message from a channel.
   *
   * @param channel the channel that received the message
   * @param data the message data
   * @throws P2pException if message processing fails
   */
  public void processMessage(Channel channel, Bytes data) throws P2pException {
    if (data == null || data.isEmpty()) {
      throw new P2pException(TypeEnum.EMPTY_MESSAGE, "");
    }

    byte firstByte = data.get(0);
    if (firstByte >= 0) {
      handMessage(channel, data);
      return;
    }

    Message message = Message.parse(p2pConfig, data);

    if (message.needToLog()) {
      log.info("Receive message from channel: {}, {}", channel.getInetSocketAddress(), message);
    } else {
      log.debug("Receive message from channel {}, {}", channel.getInetSocketAddress(), message);
    }

    switch (message.getType()) {
      case PING:
      case PONG:
        keepAliveHandler.onMessage(channel, message);
        break;
      case HANDSHAKE_HELLO:
        handshakeHandler.onMessage(channel, message);
        break;
      case STATUS:
        nodeDetectHandler.onMessage(channel, message);
        break;
      case DISCONNECT:
        channel.close();
        break;
      default:
        throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, "type:" + data.get(0));
    }
  }

  /**
   * Handle message processing and channel handshake completion.
   *
   * @param channel the channel that received the message
   * @param data the message data to process
   * @throws P2pException if message processing fails
   */
  private void handMessage(Channel channel, Bytes data) throws P2pException {
    P2pEventHandler handler = p2pConfig.handlerMap.get(data.get(0));
    if (handler == null) {
      throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, "type:" + data.get(0));
    }
    if (channel.isDiscoveryMode()) {
      channel.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.DISCOVER_MODE));
      channel.getCtx().close();
      return;
    }

    if (!channel.isFinishHandshake()) {
      channel.setFinishHandshake(true);
      DisconnectCode code = processPeer(channel);
      if (!DisconnectCode.NORMAL.equals(code)) {
        DisconnectReason disconnectReason = getDisconnectReason(code);
        channel.send(new P2pDisconnectMessage(p2pConfig, disconnectReason));
        channel.getCtx().close();
        return;
      }
      p2pConfig.handlerList.forEach(h -> h.onConnect(channel));
    }

    handler.onMessage(channel, data);
  }

  /**
   * Update the node ID for a channel and handle duplicate connections.
   *
   * @param channel the channel to update
   * @param nodeId the new node identifier
   */
  public synchronized void updateNodeId(Channel channel, String nodeId) {
    channel.setNodeId(nodeId);
    if (nodeId.equals(BytesUtils.toHexString(p2pConfig.getNodeID()))) {
      log.warn("Channel {} is myself", channel.getInetSocketAddress());
      channel.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.DUPLICATE_PEER));
      channel.close();
      return;
    }

    List<Channel> list = new ArrayList<>();
    channels
        .values()
        .forEach(
            c -> {
              if (nodeId.equals(c.getNodeId())) {
                list.add(c);
              }
            });
    if (list.size() <= 1) {
      return;
    }
    Channel c1 = list.get(0);
    Channel c2 = list.get(1);
    log.info("Close channel {}, other channel {} is earlier", c1, c2);
    if (c1.getStartTime() > c2.getStartTime()) {
      c1.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.DUPLICATE_PEER));
      c1.close();
    } else {
      c2.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.DUPLICATE_PEER));
      c2.close();
    }
  }

  /**
   * Trigger a connection attempt to the specified address.
   *
   * @param address the socket address to connect to
   */
  public void triggerConnect(InetSocketAddress address) {
    connPoolHandler.triggerConnect(address);
  }
}
