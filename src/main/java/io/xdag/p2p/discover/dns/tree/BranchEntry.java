package io.xdag.p2p.discover.dns.tree;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Represents a branch entry in a DNS tree. Branch entries contain references to child nodes in the
 * tree structure.
 */
@Slf4j(topic = "net")
public class BranchEntry implements Entry {

  /** Symbol used to split children in the branch entry */
  private static final String splitSymbol = ",";

  /** Array of child node references */
  @Getter private String[] children;

  /**
   * Constructor for BranchEntry.
   *
   * @param children array of child node references
   */
  public BranchEntry(String[] children) {
    this.children = children;
  }

  /**
   * Parse a branch entry from its string representation.
   *
   * @param e the string representation of the branch entry
   * @return the parsed BranchEntry object
   */
  public static BranchEntry parseEntry(String e) {
    String content = e.substring(branchPrefix.length());
    if (StringUtils.isEmpty(content)) {
      log.info("children size is 0, e:[{}]", e);
      return new BranchEntry(new String[0]);
    } else {
      return new BranchEntry(content.split(splitSymbol));
    }
  }

  /**
   * Convert the branch entry to its string representation.
   *
   * @return the string representation of the branch entry
   */
  @Override
  public String toString() {
    return branchPrefix + StringUtils.join(children, splitSymbol);
  }
}
