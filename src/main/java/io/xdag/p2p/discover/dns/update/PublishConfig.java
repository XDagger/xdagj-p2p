package io.xdag.p2p.discover.dns.update;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class for DNS publishing functionality. Contains all settings required for
 * publishing DNS tree records to various DNS providers.
 */
@Getter
@Setter
public class PublishConfig {

  /** Whether DNS publishing is enabled */
  private boolean dnsPublishEnable = false;

  /** Private key for signing DNS records */
  private String dnsPrivate = null;

  /** List of known tree URLs for bootstrapping */
  private List<String> knownTreeUrls = new ArrayList<>();

  /** List of static node addresses */
  private List<InetSocketAddress> staticNodes = new ArrayList<>();

  /** Domain name for DNS tree publishing */
  private String dnsDomain = null;

  /** Threshold for triggering DNS tree updates (0.0 to 1.0) */
  private double changeThreshold = 0.1;

  /** Maximum number of nodes to merge in a single tree update */
  private int maxMergeSize = 5;

  /** Type of DNS service provider */
  private DnsType dnsType = null;

  /** Access key ID for DNS service authentication */
  private String accessKeyId = null;

  /** Access key secret for DNS service authentication */
  private String accessKeySecret = null;

  /** Alibaba Cloud DNS endpoint (for AliYun) */
  private String aliDnsEndpoint = null;

  /** AWS Route53 hosted zone ID (for AWS) */
  private String awsHostZoneId = null;

  /** AWS region for Route53 operations (for AWS) */
  private String awsRegion = null;
}
