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
package io.xdag.p2p.discover.dns.sync;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import io.xdag.p2p.discover.dns.tree.RootEntry;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.TextParseException;

/**
 * Client tree for managing DNS tree synchronization and node discovery. Handles synchronization of
 * DNS tree entries and provides random node discovery.
 */
@Slf4j(topic = "net")
public class ClientTree {

  /** DNS client used for resolution */
  private final Client client;

  /** Link entry for this tree */
  @Getter @Setter private LinkEntry linkEntry;

  /** Cache for links */
  private final LinkCache linkCache;

  /** Last validation timestamp */
  private long lastValidateTime;

  /** Last sequence number */
  private int lastSeq = -1;

  /** Root entry for this tree */
  @Getter @Setter private RootEntry root;

  /** Synchronizer for ENR entries */
  private SubtreeSync enrSync;

  /** Synchronizer for link entries */
  private SubtreeSync linkSync;

  /** All links in this tree */
  private Set<String> curLinks;

  /** Root for link garbage collection */
  private String linkGCRoot;

  /** Random number generator */
  private final Random random;

  /**
   * Constructor for client tree with only client.
   *
   * @param c the DNS client
   */
  public ClientTree(Client c) {
    this.client = c;
    this.linkCache = new LinkCache();
    random = new Random();
  }

  /**
   * Constructor for client tree with client, link cache, and link entry.
   *
   * @param c the DNS client
   * @param lc the link cache
   * @param loc the link entry location
   */
  public ClientTree(Client c, LinkCache lc, LinkEntry loc) {
    this.client = c;
    this.linkCache = lc;
    this.linkEntry = loc;
    curLinks = new HashSet<>();
    random = new Random();
  }

  /**
   * Synchronize all entries in the tree.
   *
   * @param entries the map of entries to synchronize
   * @return array indicating if root was updated [lroot, eroot]
   * @throws DnsException if DNS resolution fails
   * @throws UnknownHostException if host is unknown
   * @throws SignatureException if signature verification fails
   * @throws TextParseException if DNS text parsing fails
   */
  public boolean[] syncAll(Map<String, Entry> entries)
      throws DnsException, UnknownHostException, SignatureException, TextParseException {
    boolean[] isRootUpdate = updateRoot();
    linkSync.resolveAll(entries);
    enrSync.resolveAll(entries);
    return isRootUpdate;
  }

  /**
   * Retrieve a single random entry from the tree.
   *
   * @return a DNS node if found, null otherwise
   * @throws DnsException if DNS resolution fails
   * @throws SignatureException if signature verification fails
   * @throws TextParseException if DNS text parsing fails
   * @throws UnknownHostException if host is unknown
   */
  public synchronized DnsNode syncRandom()
      throws DnsException, SignatureException, TextParseException, UnknownHostException {
    if (rootUpdateDue()) {
      updateRoot();
    }

    // Link tree sync has priority, run it to completion before syncing ENRs.
    if (!linkSync.done()) {
      syncNextLink();
      return null;
    }
    gcLinks();

    // Sync next random entry in ENR tree. Once every node has been visited, we simply
    // start over. This is fine because entries are cached internally by the client LRU
    // also by DNS resolvers.
    if (enrSync.done()) {
      enrSync = new SubtreeSync(client, linkEntry, root.getERoot(), false);
    }
    return syncNextRandomNode();
  }

  /**
   * Remove outdated links from the global link cache. Garbage collection runs once when the link
   * sync finishes.
   */
  public void gcLinks() {
    if (!linkSync.done() || root.getLRoot().equals(linkGCRoot)) {
      return;
    }
    linkCache.resetLinks(linkEntry.represent(), curLinks);
    linkGCRoot = root.getLRoot();
  }

  /**
   * Traverse next link of missing entries.
   *
   * @throws DnsException if DNS resolution fails
   * @throws TextParseException if DNS text parsing fails
   * @throws UnknownHostException if host is unknown
   */
  public void syncNextLink() throws DnsException, TextParseException, UnknownHostException {
    String hash = linkSync.missing.peek();
    Entry entry = linkSync.resolveNext(hash);
    linkSync.missing.poll();

    if (entry instanceof LinkEntry dest) {
      linkCache.addLink(linkEntry.represent(), dest.represent());
      curLinks.add(dest.represent());
    }
  }

  /**
   * Get one hash from ENR missing randomly, then get random node from hash if it's a leaf node.
   *
   * @return a random DNS node if found, null otherwise
   * @throws DnsException if DNS resolution fails
   * @throws TextParseException if DNS text parsing fails
   * @throws UnknownHostException if host is unknown
   */
  private DnsNode syncNextRandomNode()
      throws DnsException, TextParseException, UnknownHostException {
    int pos = random.nextInt(enrSync.missing.size());
    String hash = enrSync.missing.get(pos);
    Entry entry = enrSync.resolveNext(hash);
    enrSync.missing.remove(pos);
    if (entry instanceof NodesEntry nodesEntry) {
      List<DnsNode> nodeList = nodesEntry.nodes();
      int size = nodeList.size();
      return nodeList.get(random.nextInt(size));
    }
    log.info("Get branch or link entry in syncNextRandomNode");
    return null;
  }

  /**
   * Update the root entry to ensure it's up-to-date.
   *
   * @return array indicating if root was updated [lroot, eroot]
   * @throws TextParseException if DNS text parsing fails
   * @throws DnsException if DNS resolution fails
   * @throws SignatureException if signature verification fails
   * @throws UnknownHostException if host is unknown
   */
  private boolean[] updateRoot()
      throws TextParseException, DnsException, SignatureException, UnknownHostException {
    log.info("UpdateRoot {}", linkEntry.domain());
    lastValidateTime = System.currentTimeMillis();
    RootEntry rootEntry = client.resolveRoot(linkEntry);
    if (rootEntry == null) {
      return new boolean[] {false, false};
    }
    if (rootEntry.getSeq() <= lastSeq) {
      log.info("The seq of url doesn't change, url:[{}], seq:{}", linkEntry.represent(), lastSeq);
      return new boolean[] {false, false};
    }

    root = rootEntry;
    lastSeq = rootEntry.getSeq();

    boolean updateLRoot = false;
    boolean updateERoot = false;
    if (linkSync == null || !rootEntry.getLRoot().equals(linkSync.root)) {
      linkSync = new SubtreeSync(client, linkEntry, rootEntry.getLRoot(), true);
      curLinks = new HashSet<>(); // clear all links
      updateLRoot = true;
    } else {
      // if lroot is not changed, wo do not to sync the link tree
      log.info(
          "The lroot of url doesn't change, url:[{}], lroot:[{}]",
          linkEntry.represent(),
          linkSync.root);
    }

    if (enrSync == null || !rootEntry.getERoot().equals(enrSync.root)) {
      enrSync = new SubtreeSync(client, linkEntry, rootEntry.getERoot(), false);
      updateERoot = true;
    } else {
      // if eroot is not changed, wo do not to sync the enr tree
      log.info(
          "The eroot of url doesn't change, url:[{}], eroot:[{}]",
          linkEntry.represent(),
          enrSync.root);
    }
    return new boolean[] {updateLRoot, updateERoot};
  }

  /**
   * Check if root update is due based on timing.
   *
   * @return true if root update is needed
   */
  private boolean rootUpdateDue() {
    boolean scheduledCheck = System.currentTimeMillis() > nextScheduledRootCheck();
    if (scheduledCheck) {
      log.info("Update root because of scheduledCheck, {}", linkEntry.domain());
    }
    return root == null || scheduledCheck;
  }

  /**
   * Get the timestamp for the next scheduled root check.
   *
   * @return timestamp of next scheduled check
   */
  public long nextScheduledRootCheck() {
    return lastValidateTime + Client.recheckInterval * 1000L;
  }

  /**
   * String representation of this client tree.
   *
   * @return string representation
   */
  public String toString() {
    return linkEntry.toString();
  }
}
