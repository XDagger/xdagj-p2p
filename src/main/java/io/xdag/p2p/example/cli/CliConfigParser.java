package io.xdag.p2p.example.cli;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.update.DnsType;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

/**
 * CLI configuration parser for P2P applications Supports all command line options for comprehensive
 * P2P configuration
 */
@Slf4j(topic = "cli")
public class CliConfigParser {

  // CLI option constants
  private static final String OPT_HELP = "h";
  private static final String OPT_SEED_NODES = "s";
  private static final String OPT_ACTIVE_NODES = "a";
  private static final String OPT_TRUST_IPS = "t";
  private static final String OPT_MAX_CONNECTIONS = "M";
  private static final String OPT_MIN_CONNECTIONS = "m";
  private static final String OPT_MIN_ACTIVE_CONNECTIONS = "ma";
  private static final String OPT_DISCOVER = "d";
  private static final String OPT_PORT = "p";
  private static final String OPT_VERSION = "v";
  private static final String OPT_URL_SCHEMES = "u";

  // DNS publish options
  private static final String OPT_PUBLISH = "publish";
  private static final String OPT_DNS_PRIVATE = "dns-private";
  private static final String OPT_KNOWN_URLS = "known-urls";
  private static final String OPT_STATIC_NODES = "static-nodes";
  private static final String OPT_DOMAIN = "domain";
  private static final String OPT_CHANGE_THRESHOLD = "change-threshold";
  private static final String OPT_MAX_MERGE_SIZE = "max-merge-size";
  private static final String OPT_SERVER_TYPE = "server-type";
  private static final String OPT_ACCESS_KEY_ID = "access-key-id";
  private static final String OPT_ACCESS_KEY_SECRET = "access-key-secret";
  private static final String OPT_HOST_ZONE_ID = "host-zone-id";
  private static final String OPT_AWS_REGION = "aws-region";
  private static final String OPT_ALIYUN_ENDPOINT = "aliyun-dns-endpoint";

  /** Parse command line arguments and configure P2pConfig */
  public boolean parseAndConfigure(String[] args, P2pConfig config) throws CliParseException {
    try {
      CommandLine cli = parseCommandLine(args);

      if (cli.hasOption(OPT_HELP)) {
        printHelp();
        return false;
      }

      configureBasicOptions(cli, config);
      configureDnsOptions(cli, config);

      return true;
    } catch (ParseException e) {
      log.error("Failed to parse command line arguments: {}", e.getMessage());
      printHelp();
      throw new CliParseException("Invalid command line arguments", e);
    }
  }

  private CommandLine parseCommandLine(String[] args) throws ParseException {
    Options options = createOptions();
    CommandLineParser parser = new DefaultParser();
    return parser.parse(options, args);
  }

  private void configureBasicOptions(CommandLine cli, P2pConfig config) throws CliParseException {
    // Seed nodes
    if (cli.hasOption(OPT_SEED_NODES)) {
      config.setSeedNodes(parseAddressList(cli.getOptionValue(OPT_SEED_NODES)));
      log.info("Configured seed nodes: {}", config.getSeedNodes());
    }

    // Active nodes
    if (cli.hasOption(OPT_ACTIVE_NODES)) {
      config.setActiveNodes(parseAddressList(cli.getOptionValue(OPT_ACTIVE_NODES)));
      log.info("Configured active nodes: {}", config.getActiveNodes());
    }

    // Trust IPs
    if (cli.hasOption(OPT_TRUST_IPS)) {
      List<InetAddress> trustNodes = new ArrayList<>();
      String[] ips = cli.getOptionValue(OPT_TRUST_IPS).split(",");
      for (String ip : ips) {
        InetSocketAddress address = new InetSocketAddress(ip.trim(), 0);
        trustNodes.add(address.getAddress());
      }
      config.setTrustNodes(trustNodes);
      log.info("Configured trust nodes: {}", config.getTrustNodes());
    }

    // Connection limits
    if (cli.hasOption(OPT_MAX_CONNECTIONS)) {
      config.setMaxConnections(parseIntOption(cli, OPT_MAX_CONNECTIONS));
    }

    if (cli.hasOption(OPT_MIN_CONNECTIONS)) {
      config.setMinConnections(parseIntOption(cli, OPT_MIN_CONNECTIONS));
    }

    if (cli.hasOption(OPT_MIN_ACTIVE_CONNECTIONS)) {
      config.setMinActiveConnections(parseIntOption(cli, OPT_MIN_ACTIVE_CONNECTIONS));
    }

    // Validate connection limits
    if (config.getMinConnections() > config.getMaxConnections()) {
      throw new CliParseException(
          String.format(
              "minConnections (%d) cannot be greater than maxConnections (%d)",
              config.getMinConnections(), config.getMaxConnections()));
    }

    // Port
    if (cli.hasOption(OPT_PORT)) {
      config.setPort(parseIntOption(cli, OPT_PORT));
    }

    // Version (Network ID)
    if (cli.hasOption(OPT_VERSION)) {
      config.setNetworkId(parseIntOption(cli, OPT_VERSION));
    }

    // Discovery
    if (cli.hasOption(OPT_DISCOVER)) {
      int discover = parseIntOption(cli, OPT_DISCOVER);
      if (discover != 0 && discover != 1) {
        throw new CliParseException("discover option must be 0 or 1");
      }
      config.setDiscoverEnable(discover == 1);
    }

    // Tree URLs
    if (cli.hasOption(OPT_URL_SCHEMES)) {
      config.setTreeUrls(Arrays.asList(cli.getOptionValue(OPT_URL_SCHEMES).split(",")));
    }
  }

  private void configureDnsOptions(CommandLine cli, P2pConfig config) throws CliParseException {
    if (!cli.hasOption(OPT_PUBLISH)) {
      return;
    }

    PublishConfig publishConfig = new PublishConfig();
    publishConfig.setDnsPublishEnable(true);

    // DNS private key (required)
    if (!cli.hasOption(OPT_DNS_PRIVATE)) {
      throw new CliParseException("dns-private is required when publish is enabled");
    }

    String privateKey = cli.getOptionValue(OPT_DNS_PRIVATE);
    if (privateKey.length() != 64) {
      throw new CliParseException("dns-private must be a hex string of length 64");
    }

    try {
      BytesUtils.fromHexString(privateKey);
    } catch (Exception e) {
      throw new CliParseException("dns-private must be a valid hex string");
    }
    publishConfig.setDnsPrivate(privateKey);

    // Domain (required)
    if (!cli.hasOption(OPT_DOMAIN)) {
      throw new CliParseException("domain is required when publish is enabled");
    }
    publishConfig.setDnsDomain(cli.getOptionValue(OPT_DOMAIN));

    // Server type (required)
    if (!cli.hasOption(OPT_SERVER_TYPE)) {
      throw new CliParseException("server-type is required when publish is enabled");
    }

    String serverType = cli.getOptionValue(OPT_SERVER_TYPE);
    if ("aws".equalsIgnoreCase(serverType)) {
      publishConfig.setDnsType(DnsType.AwsRoute53);
    } else if ("aliyun".equalsIgnoreCase(serverType)) {
      publishConfig.setDnsType(DnsType.AliYun);
    } else {
      throw new CliParseException("server-type must be 'aws' or 'aliyun'");
    }

    // Access credentials (required)
    if (!cli.hasOption(OPT_ACCESS_KEY_ID)) {
      throw new CliParseException("access-key-id is required when publish is enabled");
    }
    publishConfig.setAccessKeyId(cli.getOptionValue(OPT_ACCESS_KEY_ID));

    if (!cli.hasOption(OPT_ACCESS_KEY_SECRET)) {
      throw new CliParseException("access-key-secret is required when publish is enabled");
    }
    publishConfig.setAccessKeySecret(cli.getOptionValue(OPT_ACCESS_KEY_SECRET));

    // AWS specific options
    if (publishConfig.getDnsType() == DnsType.AwsRoute53) {
      if (!cli.hasOption(OPT_AWS_REGION)) {
        throw new CliParseException("aws-region is required for AWS Route53");
      }
      publishConfig.setAwsRegion(cli.getOptionValue(OPT_AWS_REGION));

      if (cli.hasOption(OPT_HOST_ZONE_ID)) {
        publishConfig.setAwsHostZoneId(cli.getOptionValue(OPT_HOST_ZONE_ID));
      }
    }

    // Aliyun specific options
    if (publishConfig.getDnsType() == DnsType.AliYun) {
      if (!cli.hasOption(OPT_ALIYUN_ENDPOINT)) {
        throw new CliParseException("aliyun-dns-endpoint is required for Aliyun");
      }
      publishConfig.setAliDnsEndpoint(cli.getOptionValue(OPT_ALIYUN_ENDPOINT));
    }

    // Optional parameters
    if (cli.hasOption(OPT_KNOWN_URLS)) {
      publishConfig.setKnownTreeUrls(Arrays.asList(cli.getOptionValue(OPT_KNOWN_URLS).split(",")));
    }

    if (cli.hasOption(OPT_STATIC_NODES)) {
      publishConfig.setStaticNodes(parseAddressList(cli.getOptionValue(OPT_STATIC_NODES)));
    }

    if (cli.hasOption(OPT_CHANGE_THRESHOLD)) {
      double threshold = Double.parseDouble(cli.getOptionValue(OPT_CHANGE_THRESHOLD));
      if (threshold <= 0.0 || threshold >= 1.0) {
        throw new CliParseException("change-threshold must be between 0.0 and 1.0");
      }
      publishConfig.setChangeThreshold(threshold);
    }

    if (cli.hasOption(OPT_MAX_MERGE_SIZE)) {
      int maxMergeSize = parseIntOption(cli, OPT_MAX_MERGE_SIZE);
      if (maxMergeSize < 1 || maxMergeSize > 5) {
        throw new CliParseException("max-merge-size must be between 1 and 5");
      }
      publishConfig.setMaxMergeSize(maxMergeSize);
    }

    config.setPublishConfig(publishConfig);
  }

  private List<InetSocketAddress> parseAddressList(String addressString) {
    List<InetSocketAddress> addresses = new ArrayList<>();
    for (String address : addressString.split(",")) {
      InetSocketAddress socketAddress = NetUtils.parseInetSocketAddress(address.trim());
      if (socketAddress != null) {
        addresses.add(socketAddress);
      }
    }
    return addresses;
  }

  private int parseIntOption(CommandLine cli, String option) throws CliParseException {
    try {
      return Integer.parseInt(cli.getOptionValue(option));
    } catch (NumberFormatException e) {
      throw new CliParseException("Invalid integer value for option: " + option);
    }
  }

  private Options createOptions() {
    Options options = new Options();

    // Basic P2P options
    options.addOption(
        Option.builder(OPT_SEED_NODES)
            .longOpt("seed-nodes")
            .hasArg()
            .desc("seed node(s), required, ip:port[,ip:port[...]]")
            .build());

    options.addOption(
        Option.builder(OPT_ACTIVE_NODES)
            .longOpt("active-nodes")
            .hasArg()
            .desc("active node(s), ip:port[,ip:port[...]]")
            .build());

    options.addOption(
        Option.builder(OPT_TRUST_IPS)
            .longOpt("trust-ips")
            .hasArg()
            .desc("trust ip(s), ip[,ip[...]]")
            .build());

    options.addOption(
        Option.builder(OPT_MAX_CONNECTIONS)
            .longOpt("max-connection")
            .hasArg()
            .desc("max connection number, int, default 50")
            .build());

    options.addOption(
        Option.builder(OPT_MIN_CONNECTIONS)
            .longOpt("min-connection")
            .hasArg()
            .desc("min connection number, int, default 8")
            .build());

    options.addOption(
        Option.builder(OPT_MIN_ACTIVE_CONNECTIONS)
            .longOpt("min-active-connection")
            .hasArg()
            .desc("min active connection number, int, default 2")
            .build());

    options.addOption(
        Option.builder(OPT_PORT)
            .longOpt("port")
            .hasArg()
            .desc("UDP & TCP port, int, default 16783")
            .build());

    options.addOption(
        Option.builder(OPT_VERSION)
            .longOpt("version")
            .hasArg()
            .desc("p2p version, int, default 1")
            .build());

    options.addOption(
        Option.builder(OPT_DISCOVER)
            .longOpt("discover")
            .hasArg()
            .desc("enable p2p discover, 0/1, default 1")
            .build());

    options.addOption(
        Option.builder(OPT_URL_SCHEMES)
            .longOpt("url-schemes")
            .hasArg()
            .desc("dns url(s) to get nodes, url format tree://{pubkey}@{domain}, url[,url[...]]")
            .build());

    options.addOption(Option.builder(OPT_HELP).longOpt("help").desc("print help message").build());

    // DNS publish options
    addDnsPublishOptions(options);

    return options;
  }

  private void addDnsPublishOptions(Options options) {
    options.addOption(Option.builder().longOpt(OPT_PUBLISH).desc("enable dns publish").build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_DNS_PRIVATE)
            .hasArg()
            .desc("dns private key used to publish, required, hex string of length 64")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_KNOWN_URLS)
            .hasArg()
            .desc(
                "known dns urls to publish, url format tree://{pubkey}@{domain}, optional, url[,url[...]]")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_STATIC_NODES)
            .hasArg()
            .desc(
                "static nodes to publish, if exist then nodes from kad will be ignored, optional, ip:port[,ip:port[...]]")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_DOMAIN)
            .hasArg()
            .desc("dns domain to publish nodes, required, string")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_CHANGE_THRESHOLD)
            .hasArg()
            .desc(
                "change threshold of add and delete to publish, optional, should be > 0 and < 1.0, default 0.1")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_MAX_MERGE_SIZE)
            .hasArg()
            .desc(
                "max merge size to merge node to a leaf node in dns tree, optional, should be [1~5], default 5")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_SERVER_TYPE)
            .hasArg()
            .desc("dns server to publish, required, only aws or aliyun is support")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_ACCESS_KEY_ID)
            .hasArg()
            .desc("access key id of aws or aliyun api, required, string")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_ACCESS_KEY_SECRET)
            .hasArg()
            .desc("access key secret of aws or aliyun api, required, string")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_AWS_REGION)
            .hasArg()
            .desc(
                "if server-type is aws, it's region of aws api, such as \"eu-south-1\", required, string")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_HOST_ZONE_ID)
            .hasArg()
            .desc("if server-type is aws, it's host zone id of aws's domain, optional, string")
            .build());

    options.addOption(
        Option.builder()
            .longOpt(OPT_ALIYUN_ENDPOINT)
            .hasArg()
            .desc("if server-type is aliyun, it's endpoint of aws dns server, required, string")
            .build());
  }

  private void printHelp() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(120);

    Options basicOptions = new Options();
    Options dnsOptions = new Options();

    Options allOptions = createOptions();
    for (Option option : allOptions.getOptions()) {
      if (isDnsOption(option)) {
        dnsOptions.addOption(option);
      } else {
        basicOptions.addOption(option);
      }
    }

    formatter.printHelp("P2P Discovery Options:", basicOptions);
    System.out.println();
    formatter.printHelp("DNS Options:", dnsOptions);
  }

  private boolean isDnsOption(Option option) {
    String longOpt = option.getLongOpt();
    return longOpt != null
        && (longOpt.startsWith("dns-")
            || longOpt.equals(OPT_PUBLISH)
            || longOpt.contains("aws")
            || longOpt.contains("aliyun")
            || longOpt.equals("domain")
            || longOpt.equals("server-type")
            || longOpt.contains("access-key"));
  }

  /** Exception thrown when CLI parsing fails */
  public static class CliParseException extends Exception {
    public CliParseException(String message) {
      super(message);
    }

    public CliParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
