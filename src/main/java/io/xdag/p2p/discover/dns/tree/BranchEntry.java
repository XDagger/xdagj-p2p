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
