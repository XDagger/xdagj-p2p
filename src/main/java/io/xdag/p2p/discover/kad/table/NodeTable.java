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
package io.xdag.p2p.discover.kad.table;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

@Getter
public class NodeTable {

  /** Most table entries per bucket from one IP address, and in the whole table from one network. */
  public static final int MAX_PER_IP_PER_BUCKET = 2;
  public static final int MAX_PER_SUBNET = 10;

  private final Node node; // our node
  private transient NodeBucket[] buckets;
  private transient Map<String, NodeEntry> nodes;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  /** Whether private and loopback addresses are exempt from the per-address limits (test networks). */
  private final boolean privateUnlimited;

  public NodeTable(Node n) {
    this(n, false);
  }

  public NodeTable(Node n, boolean privateUnlimited) {
    this.node = n;
    this.privateUnlimited = privateUnlimited;
    initialize();
  }

  public final void initialize() {
    nodes = new ConcurrentHashMap<>();
    buckets = new NodeBucket[KademliaOptions.BINS];
    for (int i = 0; i < KademliaOptions.BINS; i++) {
      buckets[i] = new NodeBucket(i);
    }
  }

  public Node addNode(Node n) {
    if (n.getHostKey().equals(node.getHostKey())) {
      return null;
    }

    // Fast path: check if node already exists (read lock)
    lock.readLock().lock();
    try {
      NodeEntry entry = nodes.get(n.getHostKey());
      if (entry != null) {
        entry.touch();
        return null;
      }
    } finally {
      lock.readLock().unlock();
    }

    // Slow path: add new node (write lock)
    lock.writeLock().lock();
    try {
      // Double-check in case another thread added it
      NodeEntry entry = nodes.get(n.getHostKey());
      if (entry != null) {
        entry.touch();
        return null;
      }

      NodeEntry e = new NodeEntry(node.getId() != null ? BytesUtils.fromHexString(node.getId()) : Bytes.EMPTY, n);
      int bucketId = getBucketId(e);
      if (!withinAddressLimits(n, bucketId)) {
        // Somebody with many keys but few addresses must not be able to fill the table: without these
        // limits a single machine could become most of what this node knows about the network.
        return null;
      }
      NodeEntry lastSeen = buckets[bucketId].addNode(e);
      if (lastSeen != null) {
        return lastSeen.getNode();
      }
      nodes.put(n.getHostKey(), e);
      return null;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private boolean withinAddressLimits(Node n, int bucketId) {
    InetSocketAddress address = n.getPreferInetSocketAddress();
    if (address == null || address.getAddress() == null) {
      return false;
    }
    InetAddress ip = address.getAddress();
    if (privateUnlimited && !NetUtils.isPublicAddress(ip)) {
      return true;
    }
    int sameIpInBucket = 0;
    for (NodeEntry other : buckets[bucketId].getNodes()) {
      InetSocketAddress otherAddress = other.getNode().getPreferInetSocketAddress();
      if (otherAddress != null && ip.equals(otherAddress.getAddress())) {
        sameIpInBucket++;
      }
    }
    if (sameIpInBucket >= MAX_PER_IP_PER_BUCKET) {
      return false;
    }
    String subnet = NetUtils.subnetKey(ip);
    int sameSubnet = 0;
    for (NodeEntry other : nodes.values()) {
      InetSocketAddress otherAddress = other.getNode().getPreferInetSocketAddress();
      if (otherAddress != null && otherAddress.getAddress() != null
          && subnet.equals(NetUtils.subnetKey(otherAddress.getAddress()))) {
        sameSubnet++;
      }
    }
    return sameSubnet < MAX_PER_SUBNET;
  }

  public void dropNode(Node n) {
    lock.writeLock().lock();
    try {
      NodeEntry entry = nodes.get(n.getHostKey());
      if (entry != null) {
        nodes.remove(n.getHostKey());
        buckets[getBucketId(entry)].dropNode(entry);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean contains(Node n) {
    lock.readLock().lock();
    try {
      return nodes.containsKey(n.getHostKey());
    } finally {
      lock.readLock().unlock();
    }
  }

  public void touchNode(Node n) {
    lock.readLock().lock();
    try {
      NodeEntry entry = nodes.get(n.getHostKey());
      if (entry != null) {
        entry.touch();
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  public int getBucketsCount() {
    int i = 0;
    for (NodeBucket b : buckets) {
      if (b.getNodesCount() > 0) {
        i++;
      }
    }
    return i;
  }

  public int getBucketId(NodeEntry e) {
    int id = e.getDistance() - 1;
    return Math.max(id, 0);
  }

  public int getNodesCount() {
    lock.readLock().lock();
    try {
      return nodes.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<NodeEntry> getAllNodes() {
    lock.readLock().lock();
    try {
      return new ArrayList<>(nodes.values());
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<Node> getClosestNodes(Bytes targetId) {
    lock.readLock().lock();
    try {
      List<NodeEntry> closestEntries = new ArrayList<>(nodes.values());
      List<Node> closestNodes = new ArrayList<>(closestEntries.size());
      for (NodeEntry e : closestEntries) {
        closestNodes.add((Node) e.getNode().clone());
      }
      closestNodes.sort(new DistanceComparator(targetId));
      if (closestNodes.size() > KademliaOptions.BUCKET_SIZE) {
        closestNodes = closestNodes.subList(0, KademliaOptions.BUCKET_SIZE);
      }
      return closestNodes;
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<Node> getTableNodes() {
    lock.readLock().lock();
    try {
      List<Node> nodeList = new ArrayList<>(nodes.size());
      for (NodeEntry nodeEntry : nodes.values()) {
        nodeList.add(nodeEntry.getNode());
      }
      return nodeList;
    } finally {
      lock.readLock().unlock();
    }
  }
}
