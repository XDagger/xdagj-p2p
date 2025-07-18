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

  /** AWS Route53 hosted zone ID (for AWS) */
  private String awsHostZoneId = null;

  /** AWS region for Route53 operations (for AWS) */
  private String awsRegion = null;
}
