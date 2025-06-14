package io.xdag.p2p.discover.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.config.P2pConfig;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DnsNodeTest {

  @Test
  public void testCompressDnsNode() throws UnknownHostException, InvalidProtocolBufferException {
    P2pConfig p2pConfig = new P2pConfig();
    DnsNode[] nodes =
        new DnsNode[] {
          new DnsNode(null, null, "192.168.0.1", null, 10000),
        };
    List<DnsNode> nodeList = Arrays.asList(nodes);
    String enrContent = DnsNode.compress(nodeList);

    List<DnsNode> dnsNodes = DnsNode.decompress(p2pConfig, enrContent);
    assertEquals(1, dnsNodes.size());
    assertEquals(nodes[0], dnsNodes.getFirst());
  }

  @Test
  public void testSortDnsNode() throws UnknownHostException {
    DnsNode[] nodes =
        new DnsNode[] {
          new DnsNode(null, null, "192.168.0.1", null, 10000),
          new DnsNode(null, null, "192.168.0.2", null, 10000),
          new DnsNode(null, null, "192.168.0.3", null, 10000),
          new DnsNode(null, null, "192.168.0.4", null, 10000),
          new DnsNode(null, null, "192.168.0.5", null, 10000),
          new DnsNode(null, null, "192.168.0.6", null, 10001),
          new DnsNode(null, null, "192.168.0.6", null, 10002),
          new DnsNode(null, null, "192.168.0.6", null, 10003),
          new DnsNode(null, null, "192.168.0.6", null, 10004),
          new DnsNode(null, null, "192.168.0.6", null, 10005),
          new DnsNode(null, null, "192.168.0.10", "fe80::0001", 10005),
          new DnsNode(null, null, "192.168.0.10", "fe80::0002", 10005),
          new DnsNode(null, null, null, "fe80::0001", 10000),
          new DnsNode(null, null, null, "fe80::0002", 10000),
          new DnsNode(null, null, null, "fe80::0002", 10001),
        };
    List<DnsNode> nodeList = Arrays.asList(nodes);
    Collections.shuffle(nodeList); // random order
    Collections.sort(nodeList);
    for (int i = 0; i < nodeList.size(); i++) {
      assertEquals(nodes[i], nodeList.get(i));
    }
  }
}
