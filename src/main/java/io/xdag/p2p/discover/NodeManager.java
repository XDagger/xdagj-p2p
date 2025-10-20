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
package io.xdag.p2p.discover;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.kad.KadService;
import java.util.List;

public class NodeManager {

  private final P2pConfig p2pConfig;
  private DiscoverService discoverService;
  private DiscoverServer discoverServer;

  public NodeManager(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
  }

  public void init() {
    discoverService = new KadService(p2pConfig);
    discoverService.init();
    if (p2pConfig.isDiscoverEnable()) {
      discoverServer = new DiscoverServer(p2pConfig);
      discoverServer.init(discoverService);
    }
  }

  public void close() {
    if (discoverService != null) {
      discoverService.close();
    }
    if (discoverServer != null) {
      discoverServer.close();
    }
  }

  public List<Node> getConnectableNodes() {
    return discoverService.getConnectableNodes();
  }

  public Node getHomeNode() {
    Node home = discoverService != null ? discoverService.getPublicHomeNode() : null;
    if (home == null && p2pConfig != null) {
      home = new Node(null, p2pConfig.getIpV4(), p2pConfig.getIpV6(), p2pConfig.getPort());
    }
    return home;
  }

  public List<Node> getTableNodes() {
    return discoverService.getTableNodes();
  }

  public List<Node> getAllNodes() {
    return discoverService.getAllNodes();
  }

  // Expose boot nodes via KadService when needed
  public List<Node> getBootNodes() {
    if (discoverService instanceof io.xdag.p2p.discover.kad.KadService ks) {
      return ks.getBootNodes();
    }
    return List.of();
  }
}
