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
package io.xdag.p2p.config;

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.P2pException.TypeEnum;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class P2pConfig {

  public List<P2pEventHandler> handlerList = new ArrayList<>();
  public Map<Byte, P2pEventHandler> handlerMap = new HashMap<>();
  private List<InetSocketAddress> seedNodes = new CopyOnWriteArrayList<>();
  private List<InetSocketAddress> activeNodes = new CopyOnWriteArrayList<>();
  private List<InetAddress> trustNodes = new CopyOnWriteArrayList<>();
  /**
   * Address this node announces to others. Creating a config never talks to the network: the default is the
   * first LAN address. Set the public address explicitly, or call {@link #detectExternalIp()} to ask the
   * "what is my address" services (third parties - that is a decision for the operator, not a default).
   */
  private String ipV4 = NetUtils.getLanIpV4();
  private String lanIpV4 = ipV4;
  private String ipV6;
  private int port = 16783;
  /** Local address the TCP listener and the discovery socket bind to (null or empty: all interfaces). */
  private String bindIp;

  private int minConnections = 8;
  private int maxConnections = 50;
  private boolean discoverEnable = true;
  private boolean disconnectionPolicyEnable = false;

  // ---- admission control -------------------------------------------------------------------------------

  /**
   * false = closed network: connections are only accepted from, and made to, the configured seed / active /
   * trust nodes, and nodes learnt by discovery are ignored. The application can switch this at run time.
   */
  private volatile boolean permissionless = true;
  /** Most inbound connections (handshake done or not); the rest of {@link #maxConnections} is kept for outbound. */
  private int maxInboundConnections = 40;
  /** Most connections, in either direction, with one IP address. */
  private int maxConnectionsPerIp = 2;
  /** Most connections with one network (IPv4 /24, IPv6 /48): makes it expensive to surround a node. */
  private int maxConnectionsPerSubnet = 8;
  /** Most inbound connections that have not finished the handshake yet. */
  private int maxPendingHandshakes = 32;
  /** A connection that has not finished the handshake after this long is closed. */
  private long netHandshakeTimeout = 10_000;
  /**
   * Whether private, loopback and other non-public addresses learnt from other nodes may be pinged and dialled.
   * Off by default: otherwise any peer could point this node at localhost or at machines of its LAN. Turn it on
   * for test networks that live on one machine or one LAN. Configured seed / active nodes are always allowed.
   */
  private boolean allowPrivateAddresses = false;
  /** Inbound traffic of one connection: sustained bytes per second and burst; beyond that the peer is dropped. */
  private long maxInboundBytesPerSecond = 4L * 1024 * 1024;
  private long maxInboundBurstBytes = 32L * 1024 * 1024;
  /** Outbound data queued for a peer that does not read; beyond that the peer is dropped. */
  private int maxOutboundQueueBytes = 32 * 1024 * 1024;
  /** Discovery packets accepted from one IP address: sustained per second and burst. */
  private int maxDiscoveryPacketsPerSecond = 20;
  private int maxDiscoveryBurst = 60;

  /** Height reported in the handshake; the application may plug in its chain. */
  private java.util.function.LongSupplier latestBlockNumberSupplier = () -> 0L;

  // data directory for persistent storage (reputation, bans, etc.)
  private String dataDir = "data";

  // dns read config
  private List<String> treeUrls = new ArrayList<>();

  // dns publish config
  private PublishConfig publishConfig = new PublishConfig();

  private byte networkId = (byte)2;
  private short networkVersion = 0;

  // Prioritized network messages
  protected Set<MessageCode> netPrioritizedMessages = new HashSet<>(Arrays.asList(
      MessageCode.KAD_PING,
      MessageCode.KAD_PONG));

  private long netHandshakeExpiry = 5 * 60 * 1000;
  private int netMaxFrameBodySize = 128 * 1024;
  private int netMaxPacketSize = 4 * 1024 * 1024; // 4MB total packet limit
  private boolean enableFrameCompression = true;
  private String clientId = "xdagj-p2p/0.1.8";
  private String[] capabilities = new String[]{"DISCV5"};
  private boolean enableGenerateBlock = false;
  private String nodeTag = "default-node";

  // local node keypair (for handshake/signatures and node ID generation)
  private ECKeyPair nodeKey;

  /**
   * Ask the public "what is my address" services for this node's external addresses and announce those.
   * This sends HTTPS requests to third parties, so it only happens when the application asks for it.
   *
   * @return true if an external IPv4 or IPv6 address was found
   */
  public boolean detectExternalIp() {
    boolean found = false;
    String externalIpv4 = NetUtils.getExternalIpV4();
    if (externalIpv4 != null && !externalIpv4.trim().isEmpty()) {
      this.ipV4 = externalIpv4;
      found = true;
    }
    String externalIpv6 = NetUtils.getExternalIpV6();
    if (externalIpv6 != null && !externalIpv6.trim().isEmpty()) {
      this.ipV6 = externalIpv6;
      found = true;
    }
    return found;
  }

  /**
   * Whether the address is one the operator configured (seed, active or trust node). Such peers are chosen by
   * the operator: they may be on private addresses and they are the only peers of a closed network.
   */
  public boolean isConfiguredPeer(InetAddress address) {
    if (address == null) {
      return false;
    }
    if (trustNodes.contains(address)) {
      return true;
    }
    for (InetSocketAddress a : seedNodes) {
      if (address.equals(a.getAddress())) {
        return true;
      }
    }
    for (InetSocketAddress a : activeNodes) {
      if (address.equals(a.getAddress())) {
        return true;
      }
    }
    return false;
  }

  public void addP2pEventHandle(P2pEventHandler p2PEventHandler) throws P2pException {
    if (p2PEventHandler.getMessageTypes() != null) {
      for (Byte type : p2PEventHandler.getMessageTypes()) {
        if (handlerMap.get(type) != null) {
          throw new P2pException(TypeEnum.TYPE_ALREADY_REGISTERED, "type:" + type);
        }
      }
      for (Byte type : p2PEventHandler.getMessageTypes()) {
        handlerMap.put(type, p2PEventHandler);
      }
    }
    handlerList.add(p2PEventHandler);
  }

  /**
   * Ensures that nodeKey is initialized. If not set, generates a new random key pair.
   * 
   * <p><strong>WARNING:</strong> This method should only be used in testing environments.
   * In production, you MUST load a persistent key pair from a secure key store to ensure
   * the node ID remains consistent across restarts.
   * 
   * <p>This key pair is used for:
   * <ul>
   *   <li>Node ID generation (derived from XDAG address - 20 bytes, 160 bits)</li>
   *   <li>Handshake message signing</li>
   *   <li>Node identity verification</li>
   * </ul>
   * 
   * @throws IllegalStateException if called when nodeKey is already set
   */
  public void ensureNodeKey() {
    if (this.nodeKey == null) {
      this.nodeKey = ECKeyPair.generate();
      log.warn("Generated temporary nodeKey. This should ONLY be used in testing! " +
              "In production, load a persistent key to ensure stable node identity.");
    }
  }
  
  /**
   * Generate and set a new random node key pair.
   * 
   * <p><strong>WARNING:</strong> This should only be used in testing environments.
   * 
   * @return the generated ECKeyPair
   */
  public ECKeyPair generateNodeKey() {
    this.nodeKey = ECKeyPair.generate();
    return this.nodeKey;
  }
}
