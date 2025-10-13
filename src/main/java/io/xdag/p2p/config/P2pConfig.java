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
  private String ipV4 = getDefaultIpv4();
  private String lanIpV4 = NetUtils.getLanIpV4();
  private String ipV6 = NetUtils.getExternalIpV6();
  private int port = 16783;

  private int minConnections = 8;
  private int maxConnections = 50;
  private int minActiveConnections = 2;
  private int maxConnectionsWithSameIp = 2;
  private boolean discoverEnable = true;
  private boolean disconnectionPolicyEnable = false;
  private boolean nodeDetectEnable = false;

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
  private String clientId = "xdagj-p2p/0.1.1";
  private String[] capabilities = new String[]{"DISCV5"};
  private boolean enableGenerateBlock = false;
  private String nodeTag = "default-node";

  // local node keypair (for handshake/signatures and node ID generation)
  private ECKeyPair nodeKey;

  public boolean isEnableFrameCompression() { return enableFrameCompression; }
  public int getNetMaxPacketSize() { return netMaxPacketSize; }

  /**
   * Get the default IP address with fallback to LAN IP if external IP is not available.
   *
   * @return IP address string
   */
  private String getDefaultIpv4() {
    String externalIpv4 = NetUtils.getExternalIpV4();
    if (externalIpv4 != null && !externalIpv4.trim().isEmpty()) {
      return externalIpv4;
    }
    // Fallback to LAN IP if external IP is not available
    String lanIpv4 = NetUtils.getLanIpV4();
    return lanIpv4 != null ? lanIpv4 : "127.0.0.1";
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
   *   <li>Node ID generation (derived from public key)</li>
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
