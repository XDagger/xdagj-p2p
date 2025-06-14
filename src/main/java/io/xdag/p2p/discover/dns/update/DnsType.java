package io.xdag.p2p.discover.dns.update;

import lombok.Getter;

/**
 * Enumeration of supported DNS service providers. Defines the available DNS services that can be
 * used for DNS tree publishing.
 */
@Getter
public enum DnsType {
  /** Alibaba Cloud DNS service */
  AliYun(0, "aliyun dns server"),

  /** Amazon Web Services Route53 DNS service */
  AwsRoute53(1, "aws route53 server");

  private final Integer value;
  private final String desc;

  /**
   * Constructor for DNS type enum.
   *
   * @param value the numeric value of the DNS type
   * @param desc the description of the DNS service
   */
  DnsType(Integer value, String desc) {
    this.value = value;
    this.desc = desc;
  }
}
