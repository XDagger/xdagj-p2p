package io.xdag.p2p.discover.dns.sync;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.discover.dns.tree.BranchEntry;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.TextParseException;

@Slf4j(topic = "net")
public class SubtreeSync {

  public Client client;
  public LinkEntry linkEntry;

  public String root;

  public boolean link;
  public int leaves;

  public LinkedList<String> missing;

  public SubtreeSync(Client c, LinkEntry linkEntry, String root, boolean link) {
    this.client = c;
    this.linkEntry = linkEntry;
    this.root = root;
    this.link = link;
    this.leaves = 0;
    missing = new LinkedList<>();
    missing.add(root);
  }

  public boolean done() {
    return missing.isEmpty();
  }

  public void resolveAll(Map<String, Entry> dest)
      throws DnsException, UnknownHostException, TextParseException {
    while (!done()) {
      String hash = missing.peek();
      Entry entry = resolveNext(hash);
      if (entry != null) {
        dest.put(hash, entry);
      }
      missing.poll();
    }
  }

  public Entry resolveNext(String hash)
      throws DnsException, TextParseException, UnknownHostException {
    Entry entry = client.resolveEntry(linkEntry.domain(), hash);
    if (entry instanceof NodesEntry) {
      if (link) {
        throw new DnsException(TypeEnum.NODES_IN_LINK_TREE, "");
      }
      leaves++;
    } else if (entry instanceof LinkEntry) {
      if (!link) {
        throw new DnsException(TypeEnum.LINK_IN_NODES_TREE, "");
      }
      leaves++;
    } else if (entry instanceof BranchEntry branchEntry) {
      missing.addAll(Arrays.asList(branchEntry.getChildren()));
    }
    return entry;
  }
}
