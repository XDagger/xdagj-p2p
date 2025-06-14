package io.xdag.p2p.discover.dns.tree;

import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.discover.dns.update.AliClient;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.CryptoUtils;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@Getter
@Setter
@Slf4j(topic = "net")
public class Tree {

  public static final int HashAbbrevSize = 1 + 16 * 13 / 8; // Size of an encoded hash (plus comma)
  public static final int MaxChildren = 370 / HashAbbrevSize; // 13 children

  private P2pConfig p2pConfig;
  private RootEntry rootEntry;
  private Map<String, Entry> entries;
  private String privateKey;
  private String base32PublicKey;

  public Tree(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    init();
  }

  private void init() {
    this.entries = new ConcurrentHashMap<>();
  }

  private Entry build(List<Entry> leafs) {
    if (leafs.size() == 1) {
      return leafs.getFirst();
    }
    if (leafs.size() <= MaxChildren) {
      String[] children = new String[leafs.size()];
      for (int i = 0; i < leafs.size(); i++) {
        String subDomain = CryptoUtils.encode32AndTruncate(leafs.get(i).toString());
        children[i] = subDomain;
        this.entries.put(subDomain, leafs.get(i));
      }
      return new BranchEntry(children);
    }

    // every batch size of leaf entry construct a branch
    List<Entry> subtrees = new ArrayList<>();
    while (!leafs.isEmpty()) {
      int total = leafs.size();
      int n = Math.min(MaxChildren, total);
      Entry branch = build(leafs.subList(0, n));

      leafs = leafs.subList(n, total);
      subtrees.add(branch);

      String subDomain = CryptoUtils.encode32AndTruncate(branch.toString());
      this.entries.put(subDomain, branch);
    }
    return build(subtrees);
  }

  public void makeTree(int seq, List<String> enrs, List<String> links, String privateKey)
      throws DnsException {
    List<Entry> nodesEntryList = new ArrayList<>();
    for (String enr : enrs) {
      nodesEntryList.add(NodesEntry.parseEntry(p2pConfig, enr));
    }

    List<Entry> linkEntryList = new ArrayList<>();
    for (String link : links) {
      linkEntryList.add(LinkEntry.parseEntry(link));
    }

    init();

    Entry eRoot = build(nodesEntryList);
    String eRootStr = CryptoUtils.encode32AndTruncate(eRoot.toString());
    entries.put(eRootStr, eRoot);

    Entry lRoot = build(linkEntryList);
    String lRootStr = CryptoUtils.encode32AndTruncate(lRoot.toString());
    entries.put(lRootStr, lRoot);

    setRootEntry(new RootEntry(eRootStr, lRootStr, seq));

    if (StringUtils.isNotEmpty(privateKey)) {
      this.privateKey = privateKey;
      sign();
    }
  }

  public void sign() throws DnsException {
    if (StringUtils.isEmpty(privateKey)) {
      return;
    }
    Bytes sig =
        CryptoUtils.sigData(rootEntry.toString(), privateKey); // message don't include prefix
    rootEntry.setSignature(sig);

    BigInteger publicKeyInt =
        CryptoUtils.generateKeyPair(privateKey).getPublicKey().getEncodedBytes().toBigInteger();
    String unCompressPublicKey = BytesUtils.toHexString(Bytes.wrap(publicKeyInt.toByteArray()));

    // verify ourselves
    boolean verified;
    try {
      verified =
          CryptoUtils.verifySignature(
              unCompressPublicKey, rootEntry.toString(), rootEntry.getSignature());
    } catch (SignatureException e) {
      throw new DnsException(TypeEnum.INVALID_SIGNATURE, e);
    }
    if (!verified) {
      throw new DnsException(TypeEnum.INVALID_SIGNATURE, "");
    }
    String hexPub = CryptoUtils.compressPubKey(publicKeyInt);
    this.base32PublicKey = CryptoUtils.encode32(Bytes.fromHexString(hexPub));
  }

  public static List<String> merge(List<DnsNode> nodes, int maxMergeSize) {
    Collections.sort(nodes);
    List<String> enrs = new ArrayList<>();
    int networkA = -1;
    List<DnsNode> sub = new ArrayList<>();
    for (DnsNode dnsNode : nodes) {
      if ((networkA > -1 && dnsNode.getNetworkA() != networkA) || sub.size() >= maxMergeSize) {
        enrs.add(Entry.nodesPrefix + DnsNode.compress(sub));
        sub.clear();
      }
      sub.add(dnsNode);
      networkA = dnsNode.getNetworkA();
    }
    if (!sub.isEmpty()) {
      enrs.add(Entry.nodesPrefix + DnsNode.compress(sub));
    }
    return enrs;
  }

  // hash => lower(hash).domain
  public Map<String, String> toTXT(String rootDomain) {
    Map<String, String> dnsRecords = new HashMap<>();
    if (StringUtils.isNoneEmpty(rootDomain)) {
      dnsRecords.put(rootDomain, rootEntry.toFormat());
    } else {
      dnsRecords.put(AliClient.aliyunRoot, rootEntry.toFormat());
    }
    for (Map.Entry<String, Entry> item : entries.entrySet()) {
      String hash = item.getKey();
      String newKey = StringUtils.isNoneEmpty(rootDomain) ? hash + "." + rootDomain : hash;
      dnsRecords.put(newKey.toLowerCase(), item.getValue().toString());
    }
    return dnsRecords;
  }

  public int getSeq() {
    return rootEntry.getSeq();
  }

  public void setSeq(int seq) {
    rootEntry.setSeq(seq);
  }

  public List<String> getLinksEntry() {
    List<String> linkList = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (entry instanceof LinkEntry linkEntry) {
        linkList.add(linkEntry.toString());
      }
    }
    return linkList;
  }

  public Map<String, Entry> getLinksMap() {
    Map<String, Entry> linksMap = new HashMap<>();
    entries.entrySet().stream()
        .filter(p -> p.getValue() instanceof LinkEntry)
        .forEach(p -> linksMap.put(p.getKey(), p.getValue()));
    return linksMap;
  }

  public List<String> getBranchesEntry() {
    List<String> branches = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (entry instanceof BranchEntry branchEntry) {
        branches.add(branchEntry.toString());
      }
    }
    return branches;
  }

  public List<String> getNodesEntry() {
    List<String> nodesEntryList = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (entry instanceof NodesEntry nodesEntry) {
        nodesEntryList.add(nodesEntry.toString());
      }
    }
    return nodesEntryList;
  }

  public Map<String, Entry> getNodesMap() {
    Map<String, Entry> nodesMap = new HashMap<>();
    entries.entrySet().stream()
        .filter(p -> p.getValue() instanceof NodesEntry)
        .forEach(p -> nodesMap.put(p.getKey(), p.getValue()));
    return nodesMap;
  }

  /** get nodes from entries dynamically. when sync first time, entries change as time */
  public List<DnsNode> getDnsNodes() {
    List<String> nodesEntryList = getNodesEntry();
    List<DnsNode> nodes = new ArrayList<>();
    for (String nodesEntry : nodesEntryList) {
      String joinStr = nodesEntry.substring(Entry.nodesPrefix.length());
      List<DnsNode> subNodes;
      try {
        subNodes = DnsNode.decompress(p2pConfig, joinStr);
      } catch (InvalidProtocolBufferException | UnknownHostException e) {
        log.error("", e);
        continue;
      }
      nodes.addAll(subNodes);
    }
    return nodes;
  }
}
