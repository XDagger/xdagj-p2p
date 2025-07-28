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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.lookup.LookUpTxt;
import io.xdag.p2p.discover.dns.tree.BranchEntry;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import io.xdag.p2p.discover.dns.tree.RootEntry;
import io.xdag.p2p.discover.dns.tree.Tree;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.EncodeUtils;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

/**
 * DNS synchronization client for managing DNS tree discovery. Handles synchronization of DNS trees
 * from multiple sources and maintains a cache of entries.
 */
@Slf4j(topic = "net")
public class Client {

  /** Recheck interval in seconds (should be smaller than rootTTL) */
  public static final int recheckInterval = 60 * 60;

  /** Maximum number of entries to cache */
  public static final int cacheLimit = 2000;

  /** Number of retry attempts for random operations */
  public static final int randomRetryTimes = 10;

  private final P2pConfig p2pConfig;

  /** Cache for DNS entries */
  private final Cache<String, Entry> cache;

  /** Map of synchronized DNS trees by URL scheme */
  @Getter private final Map<String, Tree> trees = new ConcurrentHashMap<>();

  /** Map of client trees for synchronization */
  private final Map<String, ClientTree> clientTrees = new HashMap<>();

  /** Scheduled executor for DNS synchronization tasks */
  private final ScheduledExecutorService syncer =
      Executors.newSingleThreadScheduledExecutor(BasicThreadFactory.builder().namingPattern("workerthread-%d").build());

  /** Constructor for the DNS synchronization client. Initializes the cache with specified limits. */
  public Client(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    this.cache = CacheBuilder.newBuilder().maximumSize(cacheLimit).recordStats().build();
  }

  /**
   * Initialize the DNS sync client and start periodic synchronization. Schedules synchronization
   * tasks if tree URLs are configured.
   */
  public void init() {
    if (!p2pConfig.getTreeUrls().isEmpty()) {
      syncer.scheduleWithFixedDelay(this::startSync, 5, recheckInterval, TimeUnit.SECONDS);
    }
  }

  /**
   * Start synchronization of all configured DNS trees. Iterates through tree URLs and synchronizes
   * each one.
   */
  public void startSync() {
    for (String urlScheme : p2pConfig.getTreeUrls()) {
      ClientTree clientTree = clientTrees.getOrDefault(urlScheme, new ClientTree(this));
      Tree tree = trees.getOrDefault(urlScheme, new Tree(p2pConfig));
      trees.put(urlScheme, tree);
      clientTrees.put(urlScheme, clientTree);
      try {
        syncTree(urlScheme, clientTree, tree);
      } catch (Exception e) {
        log.error("SyncTree failed, url:{}", urlScheme, e);
      }
    }
  }

  /**
   * Synchronize a specific DNS tree from the given URL scheme.
   *
   * @param urlScheme the URL scheme to synchronize from
   * @param clientTree the client tree for synchronization management
   * @param tree the tree to update with synchronized data
   * @throws Exception if synchronization fails
   */
  public void syncTree(String urlScheme, ClientTree clientTree, Tree tree) throws Exception {
    LinkEntry loc = LinkEntry.parseEntry(urlScheme);
    if (clientTree == null) {
      clientTree = new ClientTree(this);
    }
    if (clientTree.getLinkEntry() == null) {
      clientTree.setLinkEntry(loc);
    }
    if (tree.getEntries().isEmpty()) {
      // when sync tree first time, we can get the entries dynamically
      clientTree.syncAll(tree.getEntries());
    } else {
      Map<String, Entry> tmpEntries = new HashMap<>();
      boolean[] isRootUpdate = clientTree.syncAll(tmpEntries);
      if (!isRootUpdate[0]) {
        tmpEntries.putAll(tree.getLinksMap());
      }
      if (!isRootUpdate[1]) {
        tmpEntries.putAll(tree.getNodesMap());
      }
      // we update the entries after sync finishes, ignore branch difference
      tree.setEntries(tmpEntries);
    }

    tree.setRootEntry(clientTree.getRoot());
    log.info(
        "SyncTree {} complete, LinkEntry size:{}, NodesEntry size:{}, node size:{}",
        urlScheme,
        tree.getLinksEntry().size(),
        tree.getNodesEntry().size(),
        tree.getDnsNodes().size());
  }

  /**
   * Resolve the root entry for a given link entry.
   *
   * @param linkEntry the link entry to resolve root for
   * @return the resolved root entry
   * @throws TextParseException if DNS text parsing fails
   * @throws DnsException if DNS resolution fails
   * @throws UnknownHostException if host is unknown
   */
  public RootEntry resolveRoot(LinkEntry linkEntry)
      throws TextParseException, DnsException, UnknownHostException {
    // do not put root in cache
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(p2pConfig, linkEntry.domain());
    if (txtRecord == null) {
      throw new DnsException(TypeEnum.LOOK_UP_ROOT_FAILED, "domain: " + linkEntry.domain());
    }
    for (String txt : txtRecord.getStrings()) {
      if (txt.startsWith(Entry.rootPrefix)) {
        return RootEntry.parseEntry(txt, linkEntry.unCompressHexPublicKey(), linkEntry.domain());
      }
    }
    throw new DnsException(TypeEnum.NO_ROOT_FOUND, "domain: " + linkEntry.domain());
  }

  /**
   * Resolve an entry from cache or fetch it from the network.
   *
   * @param domain the domain to resolve from
   * @param hash the hash of the entry to resolve
   * @return the resolved entry
   * @throws DnsException if DNS resolution fails
   * @throws TextParseException if DNS text parsing fails
   * @throws UnknownHostException if host is unknown
   */
  public Entry resolveEntry(String domain, String hash)
      throws DnsException, TextParseException, UnknownHostException {
    Entry entry = cache.getIfPresent(hash);
    if (entry != null) {
      return entry;
    }
    entry = doResolveEntry(domain, hash);
    if (entry != null) {
      cache.put(hash, entry);
    }
    return entry;
  }

  /**
   * Perform the actual DNS entry resolution from the network.
   *
   * @param domain the domain to resolve from
   * @param hash the hash of the entry to resolve
   * @return the resolved entry or null if not found
   * @throws DnsException if DNS resolution fails
   * @throws TextParseException if DNS text parsing fails
   * @throws UnknownHostException if host is unknown
   */
  private Entry doResolveEntry(String domain, String hash)
      throws DnsException, TextParseException, UnknownHostException {
    try {
      log.debug("Decode hash: {}", BytesUtils.toHexString(EncodeUtils.decode32(hash)));
    } catch (Exception e) {
      throw new DnsException(TypeEnum.OTHER_ERROR, "invalid base32 hash: " + hash);
    }
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(p2pConfig, hash, domain);
    if (txtRecord == null) {
      return null;
    }
    String txt = LookUpTxt.joinTXTRecord(txtRecord);

    Entry entry = null;
    if (txt.startsWith(Entry.branchPrefix)) {
      entry = BranchEntry.parseEntry(txt);
    } else if (txt.startsWith(Entry.linkPrefix)) {
      entry = LinkEntry.parseEntry(txt);
    } else if (txt.startsWith(Entry.nodesPrefix)) {
      entry = NodesEntry.parseEntry(p2pConfig, txt);
    }

    if (entry == null) {
      throw new DnsException(
          TypeEnum.NO_ENTRY_FOUND, String.format("hash:%s, domain:%s, txt:%s", hash, domain, txt));
    }

    String wantHash = EncodeUtils.encode32AndTruncate(entry.toString());
    if (!wantHash.equals(hash)) {
      throw new DnsException(
          TypeEnum.HASH_MISS_MATCH,
          String.format(
              "hash mismatch, want: [%s], really: [%s], content: [%s]", wantHash, hash, entry));
    }
    return entry;
  }

  /**
   * Create a new random iterator for node discovery.
   *
   * @return a new random iterator configured with available trees
   */
  public RandomIterator newIterator() {
    RandomIterator randomIterator = new RandomIterator(this);
    for (String urlScheme : p2pConfig.getTreeUrls()) {
      try {
        randomIterator.addTree(urlScheme);
      } catch (DnsException e) {
        log.error("AddTree failed {}", urlScheme, e);
      }
    }
    return randomIterator;
  }

  /**
   * Close the DNS sync client and shutdown all resources. Shuts down the synchronization executor
   * service.
   */
  public void close() {
    syncer.shutdown();
  }
}
