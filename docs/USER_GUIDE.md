xdagj-p2p can run independently or be used as a dependency.

# 1. Run independently

## 1.1 Build the project

First, build the project to generate both dependency JAR and executable JAR:

```bash
$ mvn clean package -DskipTests
```

This will generate:
- `target/xdagj-p2p-{version}.jar` - Standard library JAR for Maven Central (core library only)
- `target/xdagj-p2p-{version}-jar-with-dependencies.jar` - Executable JAR with all dependencies and examples

**JAR Usage**:
- **For Maven dependency**: Use `xdagj-p2p-{version}.jar` (library JAR for integration)
- **For testing/running**: Use `xdagj-p2p-{version}-jar-with-dependencies.jar` (standalone executable with examples)
- **For Maven deploy**: The standard `xdagj-p2p-{version}.jar` will be deployed to Maven Central

## 1.2 Run P2P Node

Command to start a p2p node:

```bash
$ java -jar target/xdagj-p2p-{version}-jar-with-dependencies.jar [options]
```

For example, with current version:
```bash
$ java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar [options]
$ java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar [options]
```

available cli options:

```bash
usage: P2P Discovery Options:
 -a,--active-nodes <arg>             active node(s), ip:port[,ip:port[...]]
    --change-threshold <arg>         change threshold of add and delete to publish, optional, should be > 0 
                                     and < 1.0, default 0.1
 -d,--discover <arg>                 enable p2p discover, 0/1, default 1
 -h,--help                           print help message
    --host-zone-id <arg>             if server-type is aws, it's host zone id of aws's domain, optional, 
                                     string
    --known-urls <arg>               known dns urls to publish, url format tree://{pubkey}@{domain}, optional,
                                     url[,url[...]]
 -M,--max-connection <arg>           max connection number, int, default 50
 -m,--min-connection <arg>           min connection number, int, default 8
 -ma,--min-active-connection <arg>   min active connection number, int, default 2
    --max-merge-size <arg>           max merge size to merge node to a leaf node in dns tree, optional, should be [1~5],
                                     default 5
 -p,--port <arg>                     UDP & TCP port, int, default 16783
 -s,--seed-nodes <arg>               seed node(s), required, ip:port[,ip:port[...]]
    --static-nodes <arg>             static nodes to publish, if exist then nodes from kad will be ignored, 
                                     optional, ip:port[,ip:port[...]]
 -t,--trust-ips <arg>                trust ip(s), ip[,ip[...]]
 -u,--url-schemes <arg>              dns url(s) to get nodes, url format tree://{pubkey}@{domain}, url[,url[...]]
 -v,--version <arg>                  p2p version, int, default 1

usage: DNS Options:
    --access-key-id <arg>         access key id of aws api,
                                  required, string
    --access-key-secret <arg>     access key secret of aws api,
                                  required, string
    --aws-region <arg>            if server-type is aws, it's region of
                                  aws api, such as "eu-south-1", required,
                                  string
    --change-threshold <arg>      change threshold of add and delete to
                                  publish, optional, should be > 0 and <
                                  1.0, default 0.1
    --dns-private <arg>           dns private key used to publish,
                                  required, hex string of length 64
    --domain <arg>                dns domain to publish nodes, required,
                                  string
    --host-zone-id <arg>          if server-type is aws, it's host zone id
                                  of aws's domain, optional, string
    --known-urls <arg>            known dns urls to publish, url format
                                  tree://{pubkey}@{domain}, optional,
                                  url[,url[...]]
    --max-merge-size <arg>        max merge size to merge node to a leaf
                                  node in dns tree, optional, should be
                                  [1~5], default 5
    --publish                     enable dns publish
    --server-type <arg>           dns server to publish, required, only
                                  aws is support
    --static-nodes <arg>          static nodes to publish, if exist then
                                  nodes from kad will be ignored,
                                  optional, ip:port[,ip:port[...]]
```

For details please
check [StartApp](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/example/StartApp.java)
.

## 1.3 Construct a p2p network using xdagj-p2p

For example:

**Node A** - starts with default configuration parameters. Let's say its IP is 127.0.0.1

```bash
$ java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar
```

**Node B** - start with seed nodes(127.0.0.1:16783). Let's say its IP is 127.0.0.2

```bash
$ java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -s 127.0.0.1:16783
```

**Node C** - start with seed nodes(127.0.0.1:16783). Let's say its IP is 127.0.0.3

```bash
$ java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -s 127.0.0.1:16783
```

After the three nodes are successfully started, the usual situation is that node B can discover node
C (or node C can discover B), and the three of them can establish a TCP connection with each other.

## 1.2 DNS-based Node Discovery

xdagj-p2p supports DNS-based node discovery using the EIP-1459 protocol. This allows XDAG nodes to discover peers through DNS records, providing a reliable fallback when DHT discovery is unavailable.

### XDAG DNS Discovery Domains

The XDAG network uses the following DNS domain structure for network identification:

```bash
# XDAG Mainnet
mainnet.xdag.io          # XDAG mainnet node discovery

# XDAG Testnet
testnet.xdag.io          # XDAG testnet node discovery
```

**Note**: The DNS tree URLs use the format `tree://PUBKEY@domain` as defined in the EIP-1459 protocol.

### DNS Provider Support

Node lists can be deployed to any DNS provider such as CloudFlare DNS, dnsimple, Amazon
Route 53 Cloud using their respective client libraries. Currently we support:
- **Amazon Route 53** (recommended for global deployment)

For more details about the EIP-1459 protocol, see: https://eips.ethereum.org/EIPS/eip-1459

### 1.2.1 Acquire your apikey from Amazon Route 53 Cloud

* Amazon Route 53 include: AWS Access Key ID、AWS Access Key Secret、Route53 Zone ID、AWS Region, get more info <https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html>

### 1.2.2 Publish nodes to XDAG networks

#### Publish XDAG Mainnet Nodes

```bash
# Publish to XDAG mainnet
java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -p 16783 -v 201910292 -d 1 \
-s <SEED_NODE_IP>:16783 \
-publish \
--dns-private <XDAG_MAINNET_DNS_PRIVATE_KEY> \
--server-type aws \
--access-key-id <AWS_Access_Key_ID> \
--access-key-secret <AWS_Access_Key_Secret> \
--aws-region us-east-1 \
--host-zone-id <Route53_Zone_ID> \
--domain mainnet.xdag.io
```

#### Publish XDAG Testnet Nodes

```bash
# Publish to XDAG testnet
java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -p 16783 -v 54321 -d 1 \
-s <SEED_NODE_IP>:16783 \
-publish \
--dns-private <XDAG_TESTNET_DNS_PRIVATE_KEY> \
--server-type aws \
--access-key-id <AWS_Access_Key_ID> \
--access-key-secret <AWS_Access_Key_Secret> \
--aws-region us-east-1 \
--host-zone-id <Route53_Zone_ID> \
--domain testnet.xdag.io
```

This program will do following periodically:

* get nodes from p2p discover service and construct a tree using these nodes
* collect txt records from dns domain with API
* compare tree with the txt records
* submit changes to dns domain with API if necessary.

We can get tree's url from log:

```bash
# For mainnet
tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io

# For testnet  
tree://BQHGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@testnet.xdag.io
```

The compressed public key corresponds to the respective dns-private key for each network.

### 1.2.3 Verify your dns txt records

You can query dns records by following commands and check if TXT type records exist:

```bash
# Verify XDAG mainnet DNS records
dig mainnet.xdag.io TXT

# Verify XDAG testnet DNS records  
dig testnet.xdag.io TXT
```

### 1.2.4 Use DNS discovery in your nodes

To use DNS discovery, you can specify the tree URLs when starting your nodes:

```bash
# Connect to XDAG mainnet using DNS discovery
java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -p 16783 -v 201910292 -d 1 \
-u tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io

# Connect to XDAG testnet using DNS discovery
java -jar target/xdagj-p2p-0.1.4-jar-with-dependencies.jar -p 16783 -v 54321 -d 1 \
-u tree://BQHGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@testnet.xdag.io
```

At last we can release the tree's url on anywhere later, such as github. So others can download this
tree to get nodes dynamically.

# 2. Use as a dependency

## 2.1 Maven Dependency

To use xdagj-p2p as a dependency in your project, add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>io.xdag</groupId>
    <artifactId>xdagj-p2p</artifactId>
    <version>0.1.4</version>
</dependency>
```

The standard dependency JAR (`xdagj-p2p-0.1.4.jar`) contains the core library code, making it suitable for use as a Maven dependency.

## 2.2 Core classes

* [P2pService](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/P2pService.java)
  is the entry class of p2p service and provides the startup interface of p2p service and the main
  interfaces provided by p2p module.
* [P2pConfig](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/config/P2pConfig.java)
  defines all the configurations of the p2p module, such as the listening port, the maximum number
  of connections, etc.
* [P2pEventHandler](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/P2pEventHandler.java)
  is the abstract class for p2p event handler.
* [Channel](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/channel/Channel.java)
  is an implementation of the TCP connection channel in the p2p module. The new connection channel
  is obtained through the `P2pEventHandler.onConnect` method.

## 2.3 Interface

### P2pService Interface

* `P2pService(P2pConfig config)` (Constructor)
    - @param: config P2pConfig
    - @return: P2pService instance
    - desc: Creates a new P2P service instance with the given configuration

* `P2pService.start()`
    - @param: (none)
    - @return: void
    - desc: Starts the P2P service. Initializes NodeManager, starts PeerServer and PeerClient, begins connection attempts to seed nodes

* `P2pService.stop()`
    - @param: (none)
    - @return: void
    - desc: Stops the P2P service gracefully. Closes all connections and cleans up resources

* `P2pService.connect(InetSocketAddress remoteAddress)`
    - @param: remoteAddress InetSocketAddress
    - @return: ChannelFuture
    - desc: Manually connect to a specific peer at the given address. Returns a ChannelFuture for async connection handling

* `P2pService.getConnectableNodes()`
    - @param: (none)
    - @return: List<Node>
    - desc: Get all nodes that can be connected (includes both table nodes and DNS nodes)

**Note**: Event handlers are registered via `P2pConfig.addP2pEventHandle()` before starting the service, not through P2pService.

### Channel Interface

* `Channel.send(Message message)`
    - @param: message Message
    - @return: void
    - desc: send a P2P message through the channel

* `Channel.send(Bytes data)`
    - @param: data Bytes (first byte should be message type)
    - @return: void
    - desc: send raw bytes data through the channel

* `Channel.close()`
    - @param: (none)
    - @return: void
    - desc: close the channel with default ban time

* `Channel.close(long banTime)`
    - @param: banTime long (ban duration in milliseconds)
    - @return: void
    - desc: close the channel and ban the peer for specified time

* `Channel.getRemoteAddress()`
    - @param: (none)
    - @return: InetSocketAddress
    - desc: get the remote peer's socket address

* `Channel.getLayeredStats()`
    - @param: (none)
    - @return: LayeredStats
    - desc: get statistics tracker for this channel (network and application layer metrics)

* `Channel.isActive()`
    - @param: (none)
    - @return: boolean
    - desc: check if the channel is active and ready for communication

* `Channel.isFinishHandshake()`
    - @param: (none)
    - @return: boolean
    - desc: check if the handshake process has completed

## 2.4 Steps for usage

1. Config p2p discover parameters
2. (optional) Config dns parameters
3. Implement P2pEventHandler and register p2p event handler
4. Start p2p service
5. Use Channel's send and close interfaces as needed
6. Use P2pService's interfaces as needed

### 2.4.1 Config discover parameters

New p2p config instance

```java
P2pConfig config = new P2pConfig();
```

Set network version (optional, default: 0)

```java
config.setNetworkVersion((short) 11111);
```

Set network ID (optional, default: 2)

```java
config.setNetworkId((byte) 2);
```

Set TCP and UDP listen port

```java
config.setPort(16783);
```

Turn node discovery on or off

```java
config.setDiscoverEnable(true);
```

Set discover seed nodes

```java
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

// For XDAG mainnet - use actual seed node IPs
List<InetSocketAddress> mainnetSeedNodeList = new ArrayList<>();
mainnetSeedNodeList.add(new InetSocketAddress("<SEED_NODE_IP_1>", 16783));
mainnetSeedNodeList.add(new InetSocketAddress("<SEED_NODE_IP_2>", 16783));
config.setSeedNodes(mainnetSeedNodeList);

// For XDAG testnet - use actual seed node IPs
List<InetSocketAddress> testnetSeedNodeList = new ArrayList<>();
testnetSeedNodeList.add(new InetSocketAddress("<SEED_NODE_IP_1>", 16783));
testnetSeedNodeList.add(new InetSocketAddress("<SEED_NODE_IP_2>", 16783));
config.setSeedNodes(testnetSeedNodeList);
```

Set active nodes
```java
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

List<InetSocketAddress> activeNodeList = new ArrayList<>();
activeNodeList.add(new InetSocketAddress("127.0.0.2", 16783));
activeNodeList.add(new InetSocketAddress("127.0.0.3", 16783));
config.setActiveNodes(activeNodeList);
```

Set trust ips

```java
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

List<InetAddress> trustNodeList = new ArrayList<>();
trustNodeList.add((new InetSocketAddress("127.0.0.2", 16783)).getAddress());
config.setTrustNodes(trustNodeList);
```

Set the minimum number of connections (default: 8)

```java
config.setMinConnections(8);
```

Set the maximum number of connections (default: 50, production recommended: 30)

```java
config.setMaxConnections(30);
```

**Note**: `minActiveConnections` and `maxConnectionsWithSameIp` are not currently available in P2pConfig. Connection management is handled automatically by the ChannelManager.

### 2.4.2 (optional) Config dns parameters if needed
Suppose these scenes in xdagj-p2p:
* you don't want to config one or many fixed seed nodes in mobile app such as wallet, because nodes may be out of service but you cannot update the app timely
* you don't known any seed node but you still want to establish tcp connection

You can config a dns tree regardless of whether discovery service is enabled or not. Assume you have tree URLs for XDAG networks:

```java
import java.util.Arrays;

// For XDAG mainnet
config.setDiscoverEnable(false);
String[] mainnetUrls = new String[] {"tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io"};
config.setTreeUrls(Arrays.asList(mainnetUrls));

// For XDAG testnet
config.setDiscoverEnable(false);
String[] testnetUrls = new String[] {"tree://BQHGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@testnet.xdag.io"};
config.setTreeUrls(Arrays.asList(testnetUrls));
```
After that, xdagj-p2p will download the nodes from the specified DNS tree periodically.

### 2.4.3 TCP Handler

Implement definition message

```java
import java.util.HashMap;
import java.util.Map;

public class TestMessage {
    protected MessageTypes type;
    protected byte[] data;
    public TestMessage(byte[] data) {
      this.type = MessageTypes.TEST;
      this.data = data;
    }

}

public enum MessageTypes {

    FIRST((byte)0x00),

    TEST((byte)0x01),

    LAST((byte)0x8f);

    private final byte type;

    MessageTypes(byte type) {
      this.type = type;
    }

    public byte getType() {
      return type;
    }

    private static final Map<Byte, MessageTypes> map = new HashMap<>();

    static {
      for (MessageTypes value : values()) {
        map.put(value.type, value);
      }
    }

    public static MessageTypes fromByte(byte type) {
      return map.get(type);
    }
  }
```

Inheritance implements the P2pEventHandler class.

* `onConnect` is called back after the TCP connection is established.
* `onDisconnect` is called back after the TCP connection is closed.
* `onMessage` is called back after receiving a message on the channel. Note that `data[0]` is the
  message type.

```java
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
// Note: MessageTypes is defined in the example code above, or use:
// import io.xdag.p2p.example.message.MessageTypes;

public class MyP2pEventHandler extends P2pEventHandler {
    private Map<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();

    public MyP2pEventHandler() {
      this.messageTypes = new HashSet<>();
      // Use MessageTypes enum defined above, or import from example package
      this.messageTypes.add(MessageTypes.TEST.getType());
    }

    @Override
    public void onConnect(Channel channel) {
      channels.put(channel.getRemoteAddress(), channel);
    }

    @Override
    public void onDisconnect(Channel channel) {
      channels.remove(channel.getRemoteAddress());
    }

    @Override
    public void onMessage(Channel channel, Bytes data) {
      byte[] messageData = data.slice(1).toArray(); // Skip first byte (message type)
      // Process incoming message
    }
}
```

### 2.4.4 Start p2p service

Start p2p service with P2pConfig and P2pEventHandler

```java
P2pService p2pService = new P2pService(config);
MyP2pEventHandler myP2pEventHandler = new MyP2pEventHandler();
try {
  config.addP2pEventHandle(myP2pEventHandler);
} catch (P2pException e) {
  // Handle registration error (e.g., duplicate message type)
  e.printStackTrace();
  return;
}
p2pService.start();
```

For details please
check [BasicExample](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/example/BasicExample.java), [DnsExample](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/example/DnsExample.java), [StartApp](https://github.com/XDagger/xdagj-p2p/blob/master/src/main/java/io/xdag/p2p/example/StartApp.java)


