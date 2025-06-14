package io.xdag.p2p.discover.dns;

import static io.xdag.p2p.message.discover.kad.KadMessage.getEndpointFromNode;

import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.proto.Discover.EndPoints;
import io.xdag.p2p.proto.Discover.EndPoints.Builder;
import io.xdag.p2p.proto.Discover.Endpoint;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.CryptoUtils;
import java.io.Serial;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a DNS node with IPv4 and IPv6 address support. Extends the base Node class with
 * DNS-specific functionality for compression and comparison.
 */
@Slf4j(topic = "net")
public class DnsNode extends Node implements Comparable<DnsNode> {

  /** Serial version UID for serialization */
  @Serial private static final long serialVersionUID = 6689513341024130226L;

  /** Hexadecimal representation of IPv4 address */
  private String v4Hex = P2pConstant.ipV4Hex;

  /** Hexadecimal representation of IPv6 address */
  private String v6Hex = P2pConstant.ipV6Hex;

  /**
   * Constructor for DnsNode.
   *
   * @param id the node ID (can be null)
   * @param hostV4 the IPv4 address string
   * @param hostV6 the IPv6 address string
   * @param port the port number
   * @throws UnknownHostException if host address is invalid
   */
  public DnsNode(P2pConfig p2pConfig, Bytes id, String hostV4, String hostV6, int port)
      throws UnknownHostException {
    /*
     * Note: The id parameter is intentionally passed as null to the parent Node class.
     * In DNS discovery, node uniqueness and comparison rely on IP and port (v4Hex/v6Hex/port),
     * not on the id field. This avoids mixing DNS node description with DHT node identity logic.
     */
    super(p2pConfig, null, hostV4, hostV6, port);
    if (StringUtils.isNotEmpty(hostV4)) {
      this.v4Hex = ipToString(hostV4);
    }
    if (StringUtils.isNotEmpty(hostV6)) {
      this.v6Hex = ipToString(hostV6);
    }
  }

  /**
   * Compress a list of DNS nodes into a base64-encoded string.
   *
   * @param nodes the list of DNS nodes to compress
   * @return base64-encoded compressed representation
   */
  public static String compress(List<DnsNode> nodes) {
    Builder builder = EndPoints.newBuilder();
    nodes.forEach(
        node -> {
          Endpoint endpoint = getEndpointFromNode(node);
          builder.addNodes(endpoint);
        });
    return CryptoUtils.encode64(Bytes.wrap(builder.build().toByteArray()));
  }

  /**
   * Decompress a base64-encoded string into a list of DNS nodes.
   *
   * @param base64Content the base64-encoded content to decompress
   * @return list of DNS nodes
   * @throws InvalidProtocolBufferException if protobuf parsing fails
   * @throws UnknownHostException if host address is invalid
   */
  public static List<DnsNode> decompress(P2pConfig p2pConfig, String base64Content)
      throws InvalidProtocolBufferException, UnknownHostException {
    Bytes data = CryptoUtils.decode64(base64Content);
    EndPoints endPoints = EndPoints.parseFrom(data.toArray());

    List<DnsNode> dnsNodes = new ArrayList<>();
    for (Endpoint endpoint : endPoints.getNodesList()) {
      DnsNode dnsNode =
          new DnsNode(
              p2pConfig,
              Bytes.wrap(endpoint.getNodeId().toByteArray()),
              new String(endpoint.getAddress().toByteArray()),
              new String(endpoint.getAddressIpv6().toByteArray()),
              endpoint.getPort());
      dnsNodes.add(dnsNode);
    }
    return dnsNodes;
  }

  /**
   * Convert an IP address string to its hexadecimal representation.
   *
   * @param ip the IP address string
   * @return hexadecimal representation of the IP address
   * @throws UnknownHostException if the IP address is invalid
   */
  public String ipToString(String ip) throws UnknownHostException {
    byte[] bytes = InetAddress.getByName(ip).getAddress();
    return BytesUtils.toHexString(Bytes.wrap(bytes));
  }

  /**
   * Get the network A class (first octet) of the IPv4 address.
   *
   * @return the first octet of the IPv4 address, or 0 if no IPv4 address
   */
  public int getNetworkA() {
    if (StringUtils.isNotEmpty(hostV4)) {
      return Integer.parseInt(hostV4.split("\\.")[0]);
    } else {
      return 0;
    }
  }

  /**
   * Compare this DNS node with another for ordering. Comparison is based on IPv4 hex, then IPv6
   * hex, then port.
   *
   * @param o the other DNS node to compare with
   * @return negative, zero, or positive integer for less than, equal to, or greater than
   */
  @Override
  public int compareTo(DnsNode o) {
    if (this.v4Hex.compareTo(o.v4Hex) != 0) {
      return this.v4Hex.compareTo(o.v4Hex);
    } else if (this.v6Hex.compareTo(o.v6Hex) != 0) {
      return this.v6Hex.compareTo(o.v6Hex);
    } else {
      return this.port - o.port;
    }
  }

  /**
   * Check equality with another object. Two DNS nodes are equal if they have the same IPv4 hex,
   * IPv6 hex, and port.
   *
   * @param o the object to compare with
   * @return true if equal, false otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DnsNode other)) {
      return false;
    }
    return v4Hex.equals(other.v4Hex) && v6Hex.equals(other.v6Hex) && port == other.port;
  }
}
