package io.xdag.p2p.config;

import com.google.protobuf.ByteString;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.P2pException.TypeEnum;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@Getter
@Setter
public class P2pConfig {

  public List<P2pEventHandler> handlerList = new ArrayList<>();
  public Map<Byte, P2pEventHandler> handlerMap = new HashMap<>();
  private List<InetSocketAddress> seedNodes = new CopyOnWriteArrayList<>();
  private List<InetSocketAddress> activeNodes = new CopyOnWriteArrayList<>();
  private List<InetAddress> trustNodes = new CopyOnWriteArrayList<>();
  private Bytes nodeID = Bytes.wrap(NetUtils.getNodeId());
  private String ip = getDefaultIp();
  private String lanIp = NetUtils.getLanIP();
  private String ipv6 = NetUtils.getExternalIpV6();
  private int port = 16783;
  private int networkId = 1;
  private int minConnections = 8;
  private int maxConnections = 50;
  private int minActiveConnections = 2;
  private int maxConnectionsWithSameIp = 2;
  private boolean discoverEnable = true;
  private boolean disconnectionPolicyEnable = false;
  private boolean nodeDetectEnable = false;

  // dns read config
  private List<String> treeUrls = new ArrayList<>();

  // dns publish config
  private PublishConfig publishConfig = new PublishConfig();

  /**
   * Get default IP address with fallback to LAN IP if external IP is not available.
   *
   * @return IP address string
   */
  private String getDefaultIp() {
    String externalIp = NetUtils.getExternalIpV4();
    if (externalIp != null && !externalIp.trim().isEmpty()) {
      return externalIp;
    }
    // Fallback to LAN IP if external IP is not available
    String lanIp = NetUtils.getLanIP();
    return lanIp != null ? lanIp : "127.0.0.1";
  }

  public void addP2pEventHandle(P2pEventHandler p2PEventHandler) throws P2pException {
    if (p2PEventHandler.getMessageTypes() != null) {
      for (Byte type : p2PEventHandler.getMessageTypes()) {
        if (handlerMap.get(type) != null) {
          throw new P2pException(TypeEnum.TYPE_ALREADY_REGISTERED, "type:" + type);
        }
      }
      for (Byte type : p2PEventHandler.getMessageTypes()) {
        handlerMap.put(type, p2PEventHandler);
      }
    }
    handlerList.add(p2PEventHandler);
  }

  public Discover.Endpoint getHomeNode() {
    Discover.Endpoint.Builder builder =
        Discover.Endpoint.newBuilder()
            .setNodeId(ByteString.copyFrom(getNodeID().toArray()))
            .setPort(getPort());
    if (StringUtils.isNotEmpty(getIp())) {
      builder.setAddress(
          ByteString.copyFrom(Objects.requireNonNull(Bytes.wrap(getIp().getBytes()).toArray())));
    }
    if (StringUtils.isNotEmpty(getIpv6())) {
      builder.setAddressIpv6(
          ByteString.copyFrom(Objects.requireNonNull(Bytes.wrap(getIpv6().getBytes()).toArray())));
    }
    return builder.build();
  }
}
