package io.xdag.p2p.discover.dns.update;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.discover.dns.tree.Tree;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Service for publishing DNS tree records to DNS providers. Manages the periodic publication of
 * node information to DNS services.
 */
@Slf4j(topic = "net")
public class PublishService {

  private final P2pConfig p2pConfig;
  private final NodeManager nodeManager;

  /** Delay between DNS publications in seconds (1 hour) */
  private static final long publishDelay = 60 * 60;

  /** Scheduled executor for DNS publishing tasks */
  private final ScheduledExecutorService publisher =
      Executors.newSingleThreadScheduledExecutor(
          new BasicThreadFactory.Builder().namingPattern("publishService").build());

  /** DNS publishing client instance */
  private Publish<?> publish;

  public PublishService(P2pConfig p2pConfig, NodeManager nodeManager) {
    this.p2pConfig = p2pConfig;
    this.nodeManager = nodeManager;
  }

  /**
   * Initialize the DNS publishing service. Sets up the DNS client and starts the publishing
   * schedule.
   */
  public void init() {
    boolean supportV4 = p2pConfig.getIp() != null;
    PublishConfig publishConfig = p2pConfig.getPublishConfig();
    if (checkConfig(supportV4, publishConfig)) {
      try {
        publish = getPublish(publishConfig);
        publish.testConnect();
      } catch (Exception e) {
        log.error("Init PublishService failed", e);
        return;
      }

      if (publishConfig.getStaticNodes() != null && !publishConfig.getStaticNodes().isEmpty()) {
        startPublish();
      } else {
        publisher.scheduleWithFixedDelay(this::startPublish, 300, publishDelay, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * Create a DNS publishing client based on configuration.
   *
   * @param config the publishing configuration
   * @return the DNS publishing client
   * @throws Exception if client creation fails
   */
  private Publish<?> getPublish(PublishConfig config) throws Exception {
    Publish<?> publish;
    if (config.getDnsType() == DnsType.AliYun) {
      publish =
          new AliClient(
              p2pConfig,
              config.getAliDnsEndpoint(),
              config.getAccessKeyId(),
              config.getAccessKeySecret(),
              config.getChangeThreshold());
    } else {
      publish =
          new AwsClient(
              p2pConfig,
              config.getAccessKeyId(),
              config.getAccessKeySecret(),
              config.getAwsHostZoneId(),
              config.getAwsRegion(),
              config.getChangeThreshold());
    }
    return publish;
  }

  /** Start the DNS publishing process. Creates a DNS tree from current nodes and publishes it. */
  private void startPublish() {
    PublishConfig config = p2pConfig.getPublishConfig();
    try {
      Tree tree = new Tree(p2pConfig);
      List<String> nodes = getNodes(config);
      tree.makeTree(1, nodes, config.getKnownTreeUrls(), config.getDnsPrivate());
      log.info("Try to publish node count:{}", tree.getDnsNodes().size());
      publish.deploy(config.getDnsDomain(), tree);
    } catch (Exception e) {
      log.error("Failed to publish dns", e);
    }
  }

  /**
   * Get the list of nodes to publish in DNS tree format.
   *
   * @param config the publishing configuration
   * @return list of node strings for DNS tree
   * @throws UnknownHostException if host resolution fails
   */
  private List<String> getNodes(PublishConfig config) throws UnknownHostException {
    Set<Node> nodes = new HashSet<>();
    if (config.getStaticNodes() != null && !config.getStaticNodes().isEmpty()) {
      for (InetSocketAddress staticAddress : config.getStaticNodes()) {
        if (staticAddress.getAddress() instanceof Inet4Address) {
          nodes.add(
              new Node(
                  p2pConfig,
                  null,
                  staticAddress.getAddress().getHostAddress(),
                  null,
                  staticAddress.getPort()));
        } else {
          nodes.add(
              new Node(
                  p2pConfig,
                  null,
                  null,
                  staticAddress.getAddress().getHostAddress(),
                  staticAddress.getPort()));
        }
      }
    } else {
      nodes.addAll(nodeManager.getConnectableNodes());
      nodes.add(nodeManager.getHomeNode());
    }
    List<DnsNode> dnsNodes = new ArrayList<>();
    for (Node node : nodes) {
      DnsNode dnsNode =
          new DnsNode(p2pConfig, node.getId(), node.getHostV4(), node.getHostV6(), node.getPort());
      dnsNodes.add(dnsNode);
    }
    return Tree.merge(dnsNodes, config.getMaxMergeSize());
  }

  /**
   * Validate the DNS publishing configuration.
   *
   * @param supportV4 whether IPv4 is supported
   * @param config the publishing configuration to validate
   * @return true if configuration is valid, false otherwise
   */
  private boolean checkConfig(boolean supportV4, PublishConfig config) {
    if (!config.isDnsPublishEnable()) {
      log.info("Dns publish service is disable");
      return false;
    }
    if (!supportV4) {
      log.error("Must have IP v4 connection to publish dns service");
      return false;
    }
    if (config.getDnsType() == null) {
      log.error("The dns server type must be specified when enabling the dns publishing service");
      return false;
    }
    if (StringUtils.isEmpty(config.getDnsDomain())) {
      log.error("The dns domain must be specified when enabling the dns publishing service");
      return false;
    }
    if (config.getDnsType() == DnsType.AliYun
        && (StringUtils.isEmpty(config.getAccessKeyId())
            || StringUtils.isEmpty(config.getAccessKeySecret())
            || StringUtils.isEmpty(config.getAliDnsEndpoint()))) {
      log.error("The configuration items related to the Aliyun dns server cannot be empty");
      return false;
    }
    if (config.getDnsType() == DnsType.AwsRoute53
        && (StringUtils.isEmpty(config.getAccessKeyId())
            || StringUtils.isEmpty(config.getAccessKeySecret())
            || config.getAwsRegion() == null)) {
      log.error("The configuration items related to the AwsRoute53 dns server cannot be empty");
      return false;
    }
    return true;
  }

  /** Close the DNS publishing service and shutdown the executor. */
  public void close() {
    if (!publisher.isShutdown()) {
      publisher.shutdown();
    }
  }
}
