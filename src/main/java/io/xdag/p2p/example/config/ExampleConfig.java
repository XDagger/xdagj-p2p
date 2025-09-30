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
package io.xdag.p2p.example.config;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.update.DnsType;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Configuration builder for P2P examples Provides fluent API for creating P2P configurations */
@Data
@Builder
public class ExampleConfig {

  // Default values
  private static final int DEFAULT_PORT = 16783;
  private static final byte DEFAULT_NETWORK_ID = (byte) 2;
  private static final int DEFAULT_MIN_CONNECTIONS = 8;
  private static final int DEFAULT_MIN_ACTIVE_CONNECTIONS = 2;
  private static final int DEFAULT_MAX_CONNECTIONS = 30;
  private static final int DEFAULT_MAX_CONNECTIONS_SAME_IP = 2;

  // Basic P2P configuration
  @Builder.Default private int port = DEFAULT_PORT;

  @Builder.Default private byte networkId = DEFAULT_NETWORK_ID;

  @Builder.Default private boolean discoverEnable = true;

  @Builder.Default private int minConnections = DEFAULT_MIN_CONNECTIONS;

  @Builder.Default private int minActiveConnections = DEFAULT_MIN_ACTIVE_CONNECTIONS;

  @Builder.Default private int maxConnections = DEFAULT_MAX_CONNECTIONS;

  @Builder.Default private int maxConnectionsWithSameIp = DEFAULT_MAX_CONNECTIONS_SAME_IP;

  // Node lists
  private List<InetSocketAddress> seedNodes;
  private List<InetSocketAddress> activeNodes;
  private List<InetAddress> trustNodes;
  private List<String> treeUrls;

  // DNS publish configuration
  private PublishConfig publishConfig;

  /** Create a basic P2P configuration */
  public static ExampleConfig basic() {
    return ExampleConfig.builder()
        .seedNodes(getDefaultSeedNodes())
        .activeNodes(getDefaultActiveNodes())
        .trustNodes(getDefaultTrustNodes())
        .build();
  }

  /** Create a DNS sync configuration (discovery disabled) */
  public static ExampleConfig dnsSync() {
    return ExampleConfig.builder().discoverEnable(false).treeUrls(getDefaultTreeUrls()).build();
  }

  /** Create a DNS publish configuration */
  public static ExampleConfig dnsPublish(
      String dnsPrivate,
      String domain,
      DnsType dnsType,
      String accessKeyId,
      String accessKeySecret) {
    PublishConfig publishConfig = new PublishConfig();
    publishConfig.setDnsPrivate(dnsPrivate);
    publishConfig.setDnsDomain(domain);
    publishConfig.setDnsType(dnsType);
    publishConfig.setAccessKeyId(accessKeyId);
    publishConfig.setAccessKeySecret(accessKeySecret);
    publishConfig.setDnsPublishEnable(true);

    return ExampleConfig.builder()
        .discoverEnable(true)
        .seedNodes(getDefaultSeedNodes())
        .publishConfig(publishConfig)
        .build();
  }

  /** Convert to P2pConfig */
  public P2pConfig toP2pConfig() {
    P2pConfig config = new P2pConfig();

    config.setPort(port);
    config.setNetworkId(networkId);
    config.setDiscoverEnable(discoverEnable);
    config.setMinConnections(minConnections);
    config.setMinActiveConnections(minActiveConnections);
    config.setMaxConnections(maxConnections);
    config.setMaxConnectionsWithSameIp(maxConnectionsWithSameIp);

    if (seedNodes != null) {
      config.setSeedNodes(seedNodes);
    }
    if (activeNodes != null) {
      config.setActiveNodes(activeNodes);
    }
    if (trustNodes != null) {
      config.setTrustNodes(trustNodes);
    }
    if (treeUrls != null) {
      config.setTreeUrls(treeUrls);
    }
    if (publishConfig != null) {
      config.setPublishConfig(publishConfig);
    }

    return config;
  }

  private static List<InetSocketAddress> getDefaultSeedNodes() {
    return Arrays.asList(
        new InetSocketAddress("seed1.example.org", DEFAULT_PORT),
        new InetSocketAddress("seed2.example.org", DEFAULT_PORT),
        new InetSocketAddress("127.0.0.1", DEFAULT_PORT));
  }

  private static List<InetSocketAddress> getDefaultActiveNodes() {
    return Arrays.asList(
        new InetSocketAddress("127.0.0.2", DEFAULT_PORT),
        new InetSocketAddress("127.0.0.3", DEFAULT_PORT));
  }

  private static List<InetAddress> getDefaultTrustNodes() {
    return Collections.singletonList(new InetSocketAddress("127.0.0.2", DEFAULT_PORT).getAddress());
  }

  private static List<String> getDefaultTreeUrls() {
    return List.of(
        "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@nodes.example.org");
  }
}
