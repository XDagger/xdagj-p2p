package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.proto.Discover;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class NetUtilsTest {

  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void testValidIp() {
    boolean flag = NetUtils.validIpV4(null);
    assertFalse(flag);
    flag = NetUtils.validIpV4("a.1.1.1");
    assertFalse(flag);
    flag = NetUtils.validIpV4("1.1.1");
    assertFalse(flag);
    flag = NetUtils.validIpV4("0.0.0.0");
    assertFalse(flag);
    flag = NetUtils.validIpV4("256.1.2.3");
    assertFalse(flag);
    flag = NetUtils.validIpV4("1.1.1.1");
    assertTrue(flag);
  }

  @Test
  public void testValidNode() {
    boolean flag = NetUtils.validNode(null);
    assertFalse(flag);

    InetSocketAddress address = new InetSocketAddress("1.1.1.1", 1000);
    Node node = new Node(p2pConfig, address);
    flag = NetUtils.validNode(node);
    assertTrue(flag);

    node.setId(Bytes.wrap(new byte[10]));
    flag = NetUtils.validNode(node);
    assertFalse(flag);

    node = new Node(p2pConfig, NetUtils.getNodeId(), "1.1.1", null, 1000);
    flag = NetUtils.validNode(node);
    assertFalse(flag);
  }

  @Test
  public void testGetNode() {
    Discover.Endpoint endpoint = Discover.Endpoint.newBuilder().setPort(100).build();
    Node node = NetUtils.getNode(p2pConfig, endpoint);
    assertEquals(100, node.getPort());
  }

  @Test
  public void testExternalIp() {
    String ip = NetUtils.getExternalIpV4();
    assertFalse(ip.startsWith("10."));
    assertFalse(ip.startsWith("192.168."));
    assertFalse(ip.startsWith("172.16."));
    assertFalse(ip.startsWith("172.17."));
    assertFalse(ip.startsWith("172.18."));
    assertFalse(ip.startsWith("172.19."));
    assertFalse(ip.startsWith("172.20."));
    assertFalse(ip.startsWith("172.21."));
    assertFalse(ip.startsWith("172.22."));
    assertFalse(ip.startsWith("172.23."));
    assertFalse(ip.startsWith("172.24."));
    assertFalse(ip.startsWith("172.25."));
    assertFalse(ip.startsWith("172.26."));
    assertFalse(ip.startsWith("172.27."));
    assertFalse(ip.startsWith("172.28."));
    assertFalse(ip.startsWith("172.29."));
    assertFalse(ip.startsWith("172.30."));
    assertFalse(ip.startsWith("172.31."));
  }

  @Test
  public void testGetIP() {
    // notice: please check that you only have one externalIP
    String ip1 = null, ip2 = null, ip3 = null;
    try {
      Method method = NetUtils.class.getDeclaredMethod("getExternalIp", String.class);
      method.setAccessible(true);
      ip1 = (String) method.invoke(NetUtils.class, P2pConstant.ipV4Urls.get(0));
      ip2 = (String) method.invoke(NetUtils.class, P2pConstant.ipV4Urls.get(1));
      ip3 = (String) method.invoke(NetUtils.class, P2pConstant.ipV4Urls.get(2));
    } catch (Exception e) {
      fail();
    }
    String ip4 = NetUtils.getExternalIpV4();

    // At least one IP service should be available (some may fail in CI environments)
    long validServices = Stream.of(ip1, ip2, ip3).filter(Objects::nonNull).count();
    assertTrue(validServices > 0, "At least one IP service should be available");
    assertNotNull(ip4, "External IP should not be null");

    // The final combined IP should be valid
    assertTrue(NetUtils.validIpV4(ip4), "Combined external IP should be valid IPv4: " + ip4);
  }

  @Test
  public void testGetLanIP() {
    String lanIpv4 = NetUtils.getLanIP();
    assertNotNull(lanIpv4);
  }

  @Test
  public void testIPv6Format() {
    String std = "fe80:0:0:0:204:61ff:fe9d:f156";
    int randomPort = 10001;
    String ip1 =
        new InetSocketAddress("fe80:0000:0000:0000:0204:61ff:fe9d:f156", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip1);

    String ip2 =
        new InetSocketAddress("fe80::204:61ff:fe9d:f156", randomPort).getAddress().getHostAddress();
    assertEquals(std, ip2);

    String ip3 =
        new InetSocketAddress("fe80:0000:0000:0000:0204:61ff:254.157.241.86", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip3);

    String ip4 =
        new InetSocketAddress("fe80:0:0:0:0204:61ff:254.157.241.86", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip4);

    String ip5 =
        new InetSocketAddress("fe80::204:61ff:254.157.241.86", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip5);

    String ip6 =
        new InetSocketAddress("FE80::204:61ff:254.157.241.86", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip6);

    String ip7 =
        new InetSocketAddress("[fe80:0:0:0:204:61ff:fe9d:f156]", randomPort)
            .getAddress()
            .getHostAddress();
    assertEquals(std, ip7);
  }

  @Test
  public void testParseIpv6() {
    InetSocketAddress address1 =
        NetUtils.parseInetSocketAddress("[2600:1f13:908:1b00:e1fd:5a84:251c:a32a]:16783");
    assertNotNull(address1);
    assertEquals(16783, address1.getPort());
    assertEquals("2600:1f13:908:1b00:e1fd:5a84:251c:a32a", address1.getAddress().getHostAddress());

    try {
      NetUtils.parseInetSocketAddress("[2600:1f13:908:1b00:e1fd:5a84:251c:a32a]:abcd");
      fail();
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      NetUtils.parseInetSocketAddress("2600:1f13:908:1b00:e1fd:5a84:251c:a32a:16783");
      fail();
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      NetUtils.parseInetSocketAddress("[2600:1f13:908:1b00:e1fd:5a84:251c:a32a:16783");
      fail();
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      NetUtils.parseInetSocketAddress("2600:1f13:908:1b00:e1fd:5a84:251c:a32a]:16783");
      fail();
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      NetUtils.parseInetSocketAddress("2600:1f13:908:1b00:e1fd:5a84:251c:a32a");
      fail();
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    InetSocketAddress address5 = NetUtils.parseInetSocketAddress("192.168.0.1:16783");
    assertNotNull(address5);
  }
}
