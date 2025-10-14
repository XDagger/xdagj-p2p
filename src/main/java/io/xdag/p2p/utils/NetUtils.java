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
package io.xdag.p2p.utils;

import io.xdag.p2p.Peer;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class NetUtils {

  /** Pre-compiled IPv4 validation pattern for performance */
  private static final Pattern IPV4_PATTERN = Pattern.compile(
      "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

  /** Pre-compiled IPv6 validation pattern for performance */
  private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

  /**
   * Validate IPv4 address using the regex pattern to avoid DNS resolver dependency. More reliable for
   * local testing with "127.0.0.1" type addresses.
   *
   * @param ip the IP address string to validate
   * @return true if valid IPv4 address for practical use
   */
  public static boolean validIpV4(String ip) {
    if (StringUtils.isEmpty(ip)) {
      return false;
    }
    try {
      // Use pre-compiled regex pattern for better performance
      if (!IPV4_PATTERN.matcher(ip).matches()) {
        return false;
      }

      // Additional check: exclude broadcast and ANY address
      return !"0.0.0.0".equals(ip) && !"255.255.255.255".equals(ip);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Validate IPv6 address using basic format checking to avoid DNS resolver dependency.
   *
   * @param ip the IP address string to validate
   * @return true if valid IPv6 address
   */
  public static boolean validIpV6(String ip) {
    if (StringUtils.isEmpty(ip)) {
      return false;
    }
    try {
      // Basic IPv6 validation: contains colons and hex characters
      if (!ip.contains(":")) {
        return false;
      }

      // Use pre-compiled regex pattern for better performance
      if (!IPV6_PATTERN.matcher(ip).matches()) {
        return false;
      }

      // Check for basic IPv6 structure
      String[] parts = ip.split(":");
      return parts.length >= 3 && parts.length <= 8;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean validNode(Node node) {
    log.debug("Validating node: {}", node);

    if (node == null || node.getId() == null) {
      log.debug("Node validation failed: node or nodeId is null");
      return false;
    }
    if (node.getId() == null || node.getId().length() == 0) {
      log.debug(
          "Node validation failed: nodeId is empty");
      return false;
    }
    if (StringUtils.isEmpty(node.getHostV4()) && StringUtils.isEmpty(node.getHostV6())) {
      log.debug("Node validation failed: both hostV4 and hostV6 are empty");
      return false;
    }
    if (StringUtils.isNotEmpty(node.getHostV4()) && !validIpV4(node.getHostV4())) {
      log.debug("Node validation failed: invalid hostV4: {}", node.getHostV4());
      return false;
    }
    if (StringUtils.isNotEmpty(node.getHostV6()) && !validIpV6(node.getHostV6())) {
      log.debug("Node validation failed: invalid hostV6: {}", node.getHostV6());
      return false;
    }

    log.debug(
        "Node validation passed: hostV4={}, hostV6={}, port={}",
        node.getHostV4(),
        node.getHostV6(),
        node.getPort());
    return true;
  }

  public static Node getNode(P2pConfig p2pConfig, Peer peer) {
    // Peer now contains plain fields; adapt by using them directly
    String hostV4 = peer.getIp();
    String hostV6 = null;
    return new Node(
        peer.getPeerId(),
        hostV4,
        hostV6,
        peer.getPort());
  }

  /**
   * Create node from peer with fallback to UDP source address. If the peer doesn't contain
   * valid IP addresses, use the UDP source address.
   *
   * @param peer the protobuf peer
   * @param sourceAddress the UDP source address as fallback
   * @return Node with valid IP address
   */
  public static Node getNodeWithFallback(
      P2pConfig p2pConfig, Peer peer, InetSocketAddress sourceAddress) {
    String hostV4 = peer.getIp();
    String hostV6 = null;

    if (StringUtils.isEmpty(hostV4) && StringUtils.isEmpty(hostV6)) {
      if (sourceAddress.getAddress() instanceof java.net.Inet4Address) {
        hostV4 = sourceAddress.getAddress().getHostAddress();
      } else if (sourceAddress.getAddress() instanceof java.net.Inet6Address) {
        hostV6 = sourceAddress.getAddress().getHostAddress();
      }
    }

    return new Node(
        peer.getPeerId(),
        hostV4,
        hostV6,
        peer.getPort());
  }

  private static String getExternalIp(String url) {
    BufferedReader in = null;
    String ip = null;
    try {
      // Use URI.toURL() instead of deprecated URL(String) constructor
      URLConnection urlConnection = URI.create(url).toURL().openConnection();
      in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
      ip = in.readLine();
      if (ip == null || ip.trim().isEmpty()) {
        throw new IOException("Invalid address: " + ip);
      }
      try {
        InetAddress.getByName(ip);
      } catch (Exception e) {
        throw new IOException("Invalid address: " + ip);
      }
      return ip;
    } catch (Exception e) {
      log.warn(
          "Fail to get {} by {}, cause:{}",
          P2pConstant.ipV4Urls.contains(url) ? "ipv4" : "ipv6",
          url,
          e.getMessage());
      return ip;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          log.debug("Failed to close BufferedReader: {}", e.getMessage());
        }
      }
    }
  }

  private static String getOuterIPv6Address() {
    Enumeration<NetworkInterface> networkInterfaces;
    try {
      networkInterfaces = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      log.warn("GetOuterIPv6Address failed", e);
      return null;
    }
    while (networkInterfaces.hasMoreElements()) {
      Enumeration<InetAddress> inetAds = networkInterfaces.nextElement().getInetAddresses();
      while (inetAds.hasMoreElements()) {
        InetAddress inetAddress = inetAds.nextElement();
        if (inetAddress instanceof Inet6Address && !isReservedAddress(inetAddress)) {
          String ipAddress = inetAddress.getHostAddress();
          int index = ipAddress.indexOf('%');
          if (index > 0) {
            ipAddress = ipAddress.substring(0, index);
          }
          return ipAddress;
        }
      }
    }
    return null;
  }

  public static Set<String> getAllLocalAddress() {
    Set<String> localIpSet = new HashSet<>();
    Enumeration<NetworkInterface> networkInterfaces;
    try {
      networkInterfaces = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      log.warn("GetAllLocalAddress failed", e);
      return localIpSet;
    }
    while (networkInterfaces.hasMoreElements()) {
      Enumeration<InetAddress> inetAds = networkInterfaces.nextElement().getInetAddresses();
      while (inetAds.hasMoreElements()) {
        InetAddress inetAddress = inetAds.nextElement();
        String ipAddress = inetAddress.getHostAddress();
        int index = ipAddress.indexOf('%');
        if (index > 0) {
          ipAddress = ipAddress.substring(0, index);
        }
        localIpSet.add(ipAddress);
      }
    }
    return localIpSet;
  }

  private static boolean isReservedAddress(InetAddress inetAddress) {
    return inetAddress.isAnyLocalAddress()
        || inetAddress.isLinkLocalAddress()
        || inetAddress.isLoopbackAddress()
        || inetAddress.isMulticastAddress();
  }

  public static String getExternalIpV4() {
    long t1 = System.currentTimeMillis();
    String ipV4 = getIp(P2pConstant.ipV4Urls);
    log.debug("GetExternalIpV4 cost {} ms", System.currentTimeMillis() - t1);
    return ipV4;
  }

  public static String getExternalIpV6() {
    long t1 = System.currentTimeMillis();
    String ipV6 = getIp(P2pConstant.ipV6Urls);
    if (null == ipV6) {
      ipV6 = getOuterIPv6Address();
    }
    log.debug("GetExternalIpV6 cost {} ms", System.currentTimeMillis() - t1);
    return ipV6;
  }

  public static InetSocketAddress parseInetSocketAddress(String para) {
    int index = para.trim().lastIndexOf(":");
    if (index > 0) {
      String host = para.substring(0, index);
      if (host.startsWith("[") && host.endsWith("]")) {
        host = host.substring(1, host.length() - 1);
      } else {
        if (host.contains(":")) {
          throw new RuntimeException(
              String.format(
                  "Invalid inetSocketAddress: \"%s\", " + "use ipv4:port or [ipv6]:port", para));
        }
      }
      int port = Integer.parseInt(para.substring(index + 1));
      return new InetSocketAddress(host, port);
    } else {
      throw new RuntimeException(
          String.format(
              "Invalid inetSocketAddress: \"%s\", " + "use ipv4:port or [ipv6]:port", para));
    }
  }

  private static String getIp(List<String> multiSrcUrls) {
    ExecutorService executor =
        Executors.newCachedThreadPool(
            BasicThreadFactory.builder().namingPattern("getIp").build());
    CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

    List<Callable<String>> tasks = new ArrayList<>();
    multiSrcUrls.forEach(url -> tasks.add(() -> getExternalIp(url)));

    for (Callable<String> task : tasks) {
      completionService.submit(task);
    }

    Future<String> future;
    String result = null;
    try {
      future = completionService.take();
      result = future.get();
    } catch (InterruptedException | ExecutionException e) {
      // ignore
    } finally {
      executor.shutdownNow();
    }

    return result;
  }

  public static String getLanIpV4() {
    try {
      // Try to get the first non-loopback address
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        Enumeration<InetAddress> inetAds = networkInterfaces.nextElement().getInetAddresses();
        while (inetAds.hasMoreElements()) {
          InetAddress inetAddress = inetAds.nextElement();
          if (!inetAddress.isLoopbackAddress()
              && !inetAddress.isLinkLocalAddress()
              && inetAddress instanceof Inet4Address) {
            return inetAddress.getHostAddress();
          }
        }
      }
    } catch (SocketException e) {
      log.warn("Can't get LAN IP, fallback to 127.0.0.1: {}", e.getMessage());
    }
    return "127.0.0.1";
  }
}
