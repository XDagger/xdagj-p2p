package io.xdag.p2p.discover.dns.tree;

/**
 * Interface defining DNS tree entry prefixes and constants. Contains the standard prefixes used in
 * DNS tree record formats.
 */
public interface Entry {

  /** Prefix for root entries in DNS tree records */
  String rootPrefix = "tree-root-v1:";

  /** Prefix for link entries in DNS tree records */
  String linkPrefix = "tree://";

  /** Prefix for branch entries in DNS tree records */
  String branchPrefix = "tree-branch:";

  /** Prefix for node entries in DNS tree records */
  String nodesPrefix = "nodes:";
}
