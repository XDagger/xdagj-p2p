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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsManager;
import io.xdag.p2p.stats.P2pStats;
import io.xdag.p2p.stats.P2pStatsManager;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j(topic = "net")
public class P2pService {

  private final P2pConfig p2pConfig;

  private final NodeManager nodeManager;
  private final DnsManager dnsManager;
  private final ChannelManager channelManager;
  private final P2pStatsManager p2pStatsManager;

  private volatile boolean isShutdown = false;

  public P2pService(final P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;

    nodeManager = new NodeManager(this.p2pConfig);
    dnsManager = new DnsManager(this.p2pConfig, this.nodeManager);

    channelManager = new ChannelManager(this.p2pConfig, nodeManager, dnsManager);
    p2pStatsManager = new P2pStatsManager();
  }

  public void start() {
    nodeManager.init();
    channelManager.init();
    dnsManager.init();
    log.info("P2p service started");

    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  public void close() {
    if (isShutdown) {
      return;
    }
    isShutdown = true;
    dnsManager.close();
    nodeManager.close();
    channelManager.close();
    log.info("P2p service closed");
  }

  public void register(P2pEventHandler p2PEventHandler) throws P2pException {
    p2pConfig.addP2pEventHandle(p2PEventHandler);
  }

  @Deprecated
  public void connect(InetSocketAddress address) {
    channelManager.connect(address);
  }

  public ChannelFuture connect(Node node, ChannelFutureListener future) {
    return channelManager.connect(node, future);
  }

  public P2pStats getP2pStats() {
    return p2pStatsManager.getP2pStats();
  }

  public List<Node> getTableNodes() {
    return nodeManager.getTableNodes();
  }

  public List<Node> getConnectableNodes() {
    Set<Node> nodes = new HashSet<>();
    nodes.addAll(nodeManager.getConnectableNodes());
    nodes.addAll(dnsManager.getDnsNodes());
    return new ArrayList<>(nodes);
  }

  public List<Node> getAllNodes() {
    Set<Node> nodes = new HashSet<>();
    nodes.addAll(nodeManager.getAllNodes());
    nodes.addAll(dnsManager.getDnsNodes());
    return new ArrayList<>(nodes);
  }

  public void updateNodeId(Channel channel, String nodeId) {
    channelManager.updateNodeId(channel, nodeId);
  }

  public int getVersion() {
    return P2pConstant.version;
  }
}
