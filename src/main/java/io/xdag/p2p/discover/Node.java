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
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.tuweni.bytes.Bytes;

@Getter
@Setter
@Slf4j(topic = "net")
public class Node implements Serializable, Cloneable {

  protected P2pConfig p2pConfig;
  private Bytes id;
  protected String hostV4;
  protected String hostV6;
  protected int port;
  private int bindPort;
  private int p2pVersion;
  private long updateTime;

  public Node(P2pConfig p2pConfig, InetSocketAddress address) {
    this.p2pConfig = p2pConfig;
    this.id = Bytes.wrap(NetUtils.getNodeId());
    if (address.getAddress() != null) {
      if (address.getAddress() instanceof Inet4Address) {
        this.hostV4 = address.getAddress().getHostAddress();
      } else {
        this.hostV6 = address.getAddress().getHostAddress();
      }
    } else {
      // Fallback: use the hostname if address resolution failed
      log.warn("Address resolution failed for {}, using hostname as fallback", address.getHostString());
      this.hostV4 = address.getHostString();
    }
    this.port = address.getPort();
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public Node(P2pConfig p2pConfig, Bytes id, String hostV4, String hostV6, int port) {
    this.p2pConfig = p2pConfig;
    this.id = id;
    this.hostV4 = hostV4;
    this.hostV6 = hostV6;
    this.port = port;
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public Node(P2pConfig p2pConfig, Bytes id, String hostV4, String hostV6, int port, int bindPort) {
    this.p2pConfig = p2pConfig;
    this.id = id;
    this.hostV4 = hostV4;
    this.hostV6 = hostV6;
    this.port = port;
    this.bindPort = bindPort;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public void updateHostV4(String hostV4) {
    if (StringUtils.isEmpty(this.hostV4) && StringUtils.isNotEmpty(hostV4)) {
      log.info("update hostV4:{} with hostV6:{}", hostV4, this.hostV6);
      this.hostV4 = hostV4;
    }
  }

  public void updateHostV6(String hostV6) {
    if (StringUtils.isEmpty(this.hostV6) && StringUtils.isNotEmpty(hostV6)) {
      log.info("update hostV6:{} with hostV4:{}", hostV6, this.hostV4);
      this.hostV6 = hostV6;
    }
  }

  // use standard ipv6 format
  private void formatHostV6() {
    if (StringUtils.isNotEmpty(this.hostV6)) {
      try {
        InetSocketAddress addr = new InetSocketAddress(hostV6, port);
        if (addr.getAddress() != null) {
          this.hostV6 = addr.getAddress().getHostAddress();
        }
      } catch (Exception e) {
        log.warn("Failed to format IPv6 address: {}", hostV6, e);
      }
    }
  }

  public boolean isConnectible(int argsP2PVersion) {
    return port == bindPort && p2pVersion == argsP2PVersion;
  }

  public InetSocketAddress getPreferInetSocketAddress() {
    // First try IPv4 if both node and local config have IPv4
    if (StringUtils.isNotEmpty(hostV4)
        && p2pConfig != null
        && StringUtils.isNotEmpty(p2pConfig.getIpV4())) {
      return getInetSocketAddressV4();
    }
    // Then try IPv6 if both node and local config have IPv6
    else if (StringUtils.isNotEmpty(hostV6)
        && p2pConfig != null
        && StringUtils.isNotEmpty(p2pConfig.getIpV6())) {
      return getInetSocketAddressV6();
    }
    // Fallback: if local config doesn't have external IP, but node has valid address, use it anyway
    else if (StringUtils.isNotEmpty(hostV4)) {
      return getInetSocketAddressV4();
    } else if (StringUtils.isNotEmpty(hostV6)) {
      return getInetSocketAddressV6();
    } else {
      return null;
    }
  }

  public String getHexId() {
    return id == null ? null : BytesUtils.toHexString(id);
  }

  public String getHostKey() {
    InetSocketAddress address = getPreferInetSocketAddress();
    if (address == null || address.getAddress() == null) {
      log.warn(
          "Node has no valid address - hostV4: {}, hostV6: {}, port: {}", hostV4, hostV6, port);
      return null;
    }
    return address.getAddress().getHostAddress();
  }

  public String getIdString() {
    if (id == null) {
      return null;
    }
    return new String(id.toArray(), StandardCharsets.UTF_8);
  }

  public void touch() {
    updateTime = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "Node{"
        + " hostV4='"
        + hostV4
        + '\''
        + ", hostV6='"
        + hostV6
        + '\''
        + ", port="
        + port
        + ", id='"
        + (id == null ? "null" : BytesUtils.toHexString(id))
        + "'}";
  }

  public String format() {
    return "Node{"
        + " hostV4='"
        + hostV4
        + '\''
        + ", hostV6='"
        + hostV6
        + '\''
        + ", port="
        + port
        + '}';
  }

  @Override
  public int hashCode() {
    return this.format().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if (o == this) {
      return true;
    }

    if (o.getClass() == getClass()) {
      return Strings.CS.equals(getIdString(), ((Node) o).getIdString());
    }

    return false;
  }

  public InetSocketAddress getInetSocketAddressV4() {
    return StringUtils.isNotEmpty(hostV4) ? new InetSocketAddress(hostV4, port) : null;
  }

  public InetSocketAddress getInetSocketAddressV6() {
    return StringUtils.isNotEmpty(hostV6) ? new InetSocketAddress(hostV6, port) : null;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException ignored) {
    }
    return null;
  }
}
