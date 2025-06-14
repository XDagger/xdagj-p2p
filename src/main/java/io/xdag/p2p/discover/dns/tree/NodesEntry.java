package io.xdag.p2p.discover.dns.tree;

import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.config.P2pConfig;
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
  public static NodesEntry parseEntry(P2pConfig p2pConfig, String e) throws DnsException {
    String content = e.substring(nodesPrefix.length());
    List<DnsNode> nodeList;
    try {
      nodeList = DnsNode.decompress(p2pConfig, content.replace("\"", ""));
    } catch (InvalidProtocolBufferException | UnknownHostException ex) {
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
