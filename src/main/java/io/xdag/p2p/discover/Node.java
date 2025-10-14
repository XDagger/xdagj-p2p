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

import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Represents a network node in the XDAG P2P network.
 * 
 * <p>Node ID Generation:
 * The node ID is derived from the node's XDAG address (ECKeyPair.toAddress()) for security and identity verification:
 * <ul>
 *   <li>Uses XDAG address format (20 bytes, 160 bits) - Kademlia standard</li>
 *   <li>Enables cryptographic verification of node identity</li>
 *   <li>Prevents Sybil attacks and node ID spoofing</li>
 *   <li>Integrates with XDAG's address system (xdagj-crypto)</li>
 * </ul>
 * 
 * @see io.xdag.p2p.discover.kad.KadService#init()
 * @see io.xdag.p2p.config.P2pConfig#ensureNodeKey()
 */
@Getter
@Setter
@Slf4j(topic = "net")
public class Node implements Serializable, Cloneable {
    private byte networkId;
    private short networkVersion;
    
    /**
     * Node ID derived from XDAG address (20 bytes = 40 hex chars).
     * This ID is used for:
     * - Kademlia DHT distance calculations (160-bit standard)
     * - Node identity verification
     * - Preventing node impersonation
     */
    private String id;

    protected String hostV4;
    protected String hostV6;
    protected int port;
    private int bindPort;

    private long timestamp;
    private long updateTime;

    public Node(String id, InetSocketAddress address) {
        this.id = id;
        if (address.getAddress() != null) {
            if (address.getAddress() instanceof Inet4Address) {
                this.hostV4 = address.getAddress().getHostAddress();
            } else {
                this.hostV6 = address.getAddress().getHostAddress();
            }
        } else {
            log.warn("Address resolution failed for {}, using hostname as fallback", address.getHostString());
            this.hostV4 = address.getHostString();
        }
        this.port = address.getPort();
        this.bindPort = port;
        this.updateTime = System.currentTimeMillis();
        formatHostV6();
    }

    public byte[] toBytes() {
        SimpleEncoder encoder = new SimpleEncoder();
        encoder.writeByte(networkId);
        encoder.writeShort(networkVersion);
        encoder.writeString(id == null ? "" : id);

        encoder.writeString(hostV4 == null ? "" : hostV4);
        encoder.writeString(hostV6 == null ? "" : hostV6);
        encoder.writeInt(port);
        encoder.writeInt(bindPort);

        encoder.writeLong(timestamp);

        return encoder.toBytes();
    }

    public Node(byte[] bytes) {
        SimpleDecoder decoder = new SimpleDecoder(bytes);
        networkId = decoder.readByte();
        networkVersion = decoder.readShort();
        id = decoder.readString();
        hostV4 = decoder.readString();
        hostV6 = decoder.readString();
        port = decoder.readInt();
        bindPort = decoder.readInt();
        timestamp = decoder.readLong();
    }

    public Node(String id, String hostV4, String hostV6, int port) {
        this.id = id;
        this.hostV4 = hostV4;
        this.hostV6 = hostV6;
        this.port = port;
        this.bindPort = port;
        this.updateTime = System.currentTimeMillis();
        formatHostV6();
    }

    public Node(String id, String hostV4, String hostV6, int port, int bindPort) {
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

    public boolean isConnectible(byte argNetworkId) {
        return port == bindPort && networkId == argNetworkId;
    }

    public InetSocketAddress getPreferInetSocketAddress() {
        if (StringUtils.isNotEmpty(hostV4)) {
            return getInetSocketAddressV4();
        } else if (StringUtils.isNotEmpty(hostV6)) {
            return getInetSocketAddressV6();
        }
        return null;
    }

    public String getHostKey() {
        InetSocketAddress address = getPreferInetSocketAddress();
        if (address == null || address.getAddress() == null) {
            log.warn(
                    "Node has no valid address - hostV4: {}, hostV6: {}, port: {}", hostV4, hostV6, port);
            return null;
        }
        return address.getAddress().getHostAddress() + ":" + port;
    }

    public void touch() {
        updateTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "Node {" +
                ", id='" + (id == null ? "null" : id) +
                ", hostV4='" + hostV4 + '\'' +
                ", hostV6='" + hostV6 + '\'' +
                ", port=" + port +
                "'}";
    }

    public String format() {
        return "Node {" +
                ", id='" + (id == null ? "null" : id) +
                ", hostV4='" + hostV4 + '\'' +
                ", hostV6='" + hostV6 + '\'' +
                ", port=" + port +
                '}';
    }

    @Override
    public int hashCode() {
        // Prefer stable id-based hash when available
        if (id != null) {
            return id.hashCode();
        }
        // Fallback to address/port
        String host = hostV4 != null ? hostV4 : hostV6 != null ? hostV6 : "";
        return (host + ":" + port).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Node other)) {
            return false;
        }
        // If both have non-null ids, compare by id
        if (this.id != null && other.id != null) {
            return this.id.equals(other.id);
        }
        // Otherwise compare by address and port
        String thisHost = this.hostV4 != null ? this.hostV4 : this.hostV6;
        String otherHost = other.hostV4 != null ? other.hostV4 : other.hostV6;
        return this.port == other.port && thisHost != null && thisHost.equals(otherHost);
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
        } catch (CloneNotSupportedException e) {
            // This should never happen since we implement Cloneable
            throw new AssertionError("Clone not supported for Node", e);
        }
    }
}
