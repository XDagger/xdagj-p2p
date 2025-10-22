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

import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.discover.dns.DnsNode;
import java.net.UnknownHostException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a nodes entry in a DNS tree. Contains a list of DNS nodes with their network
 * information.
 *
 * @param represent String representation of the nodes entry
 * @param nodes List of DNS nodes contained in this entry
 */
@Slf4j(topic = "net")
public record NodesEntry(String represent, List<DnsNode> nodes) implements Entry {

  /**
   * Constructor for NodesEntry.
   *
   * @param represent the string representation of the entry
   * @param nodes the list of DNS nodes
   */
  public NodesEntry {}

  /**
   * Parse a nodes entry from its string representation.
   *
   * @param e the string representation of the nodes entry
   * @return the parsed NodesEntry object
   * @throws DnsException if parsing fails due to invalid format
   */
  public static NodesEntry parseEntry(String e) throws DnsException {
    String content = e.substring(nodesPrefix.length());
    List<DnsNode> nodeList;
    try {
      nodeList = DnsNode.decompress(content.replace("\"", ""));
    } catch (UnknownHostException ex) {
      throw new DnsException(TypeEnum.INVALID_NODES, ex);
    }
    return new NodesEntry(e, nodeList);
  }

  /**
   * Convert the nodes entry to its string representation.
   *
   * @return the string representation of the nodes entry
   */
  @NotNull
  @Override
  public String toString() {
    return represent;
  }
}
