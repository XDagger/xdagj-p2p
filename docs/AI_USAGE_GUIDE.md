# XDAGJ-P2P AI Usage Guide

This document is designed to help AI models understand and use the xdagj-p2p library effectively. It provides comprehensive API references, usage patterns, and examples in a structured format optimized for AI comprehension.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture and Core Components](#architecture-and-core-components)
3. [Core API Reference](#core-api-reference)
4. [Usage Patterns and Workflows](#usage-patterns-and-workflows)
5. [Configuration Reference](#configuration-reference)
6. [Complete Code Examples](#complete-code-examples)
7. [Common Tasks and Scenarios](#common-tasks-and-scenarios)
8. [Error Handling and Best Practices](#error-handling-and-best-practices)

---

## Project Overview

### Basic Information

- **Project Name**: xdagj-p2p
- **Version**: 0.1.5
- **Purpose**: High-performance Java P2P networking library for the XDAG blockchain ecosystem
- **Language**: Java 21+
- **License**: MIT

### Core Features

1. **Kademlia DHT Discovery**: Distributed hash table protocol for decentralized peer discovery
   - 160-bit Node ID based on XDAG address
   - UDP-based PING/PONG for liveness detection
   - Recursive node discovery via FIND_NODE/NEIGHBORS
   - Self-healing network topology

2. **Node Reputation System**: Persistent disk-based scoring (0-200 range)
   - Time-based reputation decay (5 points/day towards neutral)
   - Automatic save with atomic file operations
   - Thread-safe concurrent operations

3. **Ban Management**: Graduated ban durations for repeat offenders
   - 2x ban duration per offense, max 30 days
   - Whitelist support for trusted nodes
   - Automatic expiry and cleanup

4. **DNS Discovery (EIP-1459)**: Alternative discovery mechanism via DNS
   - Support for AWS Route53 DNS publishing
   - Tree-based node list distribution
   - Fallback when DHT discovery is unavailable

5. **Layered Statistics**: Network and application layer metrics
   - Network Layer: TCP/Netty frame-level metrics
   - Application Layer: Business logic metrics
   - Per-channel statistics tracking

### Technology Stack

- **Java 21**: Virtual threads and modern APIs
- **Netty 4.2.6.Final**: Async I/O framework
- **Custom SimpleCodec**: High-performance binary encoding
- **Hyperledger Besu**: Crypto primitives
- **BouncyCastle**: Crypto provider
- **Tuweni**: Packet processing utilities

### Maven Dependency

```xml
<dependency>
    <groupId>io.xdag</groupId>
    <artifactId>xdagj-p2p</artifactId>
    <version>0.1.5</version>
</dependency>
```

---

## Architecture and Core Components

### Component Hierarchy

```
P2pService (Main Entry Point)
├── P2pConfig (Configuration)
├── NodeManager (Node Discovery & Management)
├── ChannelManager (Connection Management)
├── PeerServer (TCP Server)
└── PeerClient (TCP Client)
```

### Key Classes and Their Roles

1. **P2pService**: Main service class that orchestrates all P2P functionality
2. **P2pConfig**: Configuration container for all P2P parameters
3. **P2pEventHandler**: Abstract base class for handling P2P events
4. **Channel**: Represents a TCP connection channel for message exchange
5. **Node**: Represents a discovered P2P node with address and ID
6. **NodeManager**: Manages node discovery via Kademlia DHT
7. **ChannelManager**: Manages TCP connections and channel lifecycle

### Data Flow

```
1. Application → P2pService.start()
   ↓
2. P2pService initializes NodeManager, ChannelManager, PeerServer, PeerClient
   ↓
3. PeerServer starts listening for incoming connections
   ↓
4. PeerClient attempts connections to seed nodes
   ↓
5. NodeManager discovers peers via Kademlia DHT
   ↓
6. ChannelManager establishes TCP connections
   ↓
7. Messages flow through Channel.send() → Network → Channel.onMessage()
   ↓
8. P2pEventHandler callbacks notify application of events
```

---

## Core API Reference

### P2pService

The main entry point for P2P functionality. Provides lifecycle management and connection interfaces.

#### Constructor

```java
P2pService(P2pConfig config)
```

Creates a new P2P service instance with the given configuration.

#### Key Methods

**start()**
- **Purpose**: Starts the P2P service
- **Parameters**: None
- **Returns**: void
- **Behavior**: 
  - Initializes NodeManager
  - Starts PeerServer and PeerClient
  - Begins connection attempts to seed nodes
  - Registers shutdown hook
- **Usage**: Call after configuration and event handler registration

**stop()**
- **Purpose**: Stops the P2P service gracefully
- **Parameters**: None
- **Returns**: void
- **Behavior**: 
  - Stops all connections
  - Closes NodeManager
  - Cleans up resources
- **Usage**: Call during application shutdown

**connect(InetSocketAddress remoteAddress)**
- **Purpose**: Manually connect to a specific peer
- **Parameters**: 
  - `remoteAddress`: The socket address of the peer to connect to
- **Returns**: `ChannelFuture` - Netty future for connection result
- **Usage**: Connect to a specific node programmatically

**getConnectableNodes()**
- **Purpose**: Get list of nodes that can be connected
- **Parameters**: None
- **Returns**: `List<Node>` - List of connectable nodes
- **Usage**: Query available peers for connection

### P2pConfig

Configuration container for all P2P service parameters.

#### Key Configuration Properties

**Network Configuration**
- `port` (int): TCP/UDP listen port (default: 16783)
- `ipV4` (String): IPv4 address to bind to
- `networkId` (byte): Network identifier (default: 2)
- `networkVersion` (short): Network protocol version (default: 0)

**Connection Configuration**
- `minConnections` (int): Minimum number of connections (default: 8)
- `maxConnections` (int): Maximum number of connections (default: 50)
- `discoverEnable` (boolean): Enable Kademlia DHT discovery (default: true)

**Node Discovery**
- `seedNodes` (List<InetSocketAddress>): Initial seed nodes for bootstrap
- `activeNodes` (List<InetSocketAddress>): Active nodes to maintain connections
- `trustNodes` (List<InetAddress>): Trusted IP addresses (whitelist)
- `treeUrls` (List<String>): DNS tree URLs for EIP-1459 discovery

**Node Identity**
- `nodeKey` (ECKeyPair): Node keypair for handshake and node ID generation
  - **CRITICAL**: In production, must load persistent key from secure storage
  - If null, ephemeral key is generated (testing only)
  - Node ID is derived from XDAG address (20 bytes, 160 bits)

**Advanced Configuration**
- `netHandshakeExpiry` (long): Handshake timeout in milliseconds (default: 5 minutes)
- `netMaxFrameBodySize` (int): Maximum frame body size (default: 128KB)
- `netMaxPacketSize` (int): Maximum total packet size (default: 4MB)
- `enableFrameCompression` (boolean): Enable frame compression (default: true)
- `dataDir` (String): Data directory for persistent storage (default: "data")

**Event Handler Registration**
- `addP2pEventHandle(P2pEventHandler handler)`: Register event handler
  - Validates message type conflicts
  - Registers handler for specified message types
  - Throws `P2pException` if type already registered

### P2pEventHandler

Abstract base class for handling P2P events. Extend this class to implement custom event handling.

#### Lifecycle Methods

**onConnect(Channel channel)**
- **Called when**: A new TCP connection is established
- **Parameters**: 
  - `channel`: The Channel object representing the connection
- **Returns**: void
- **Usage**: Initialize per-connection state, send greeting messages

**onDisconnect(Channel channel)**
- **Called when**: A TCP connection is closed
- **Parameters**: 
  - `channel`: The Channel object representing the connection
- **Returns**: void
- **Usage**: Clean up per-connection state

**onMessage(Channel channel, Bytes data)**
- **Called when**: A message is received on a channel
- **Parameters**: 
  - `channel`: The Channel object
  - `data`: Raw message bytes (first byte is message type)
- **Returns**: void
- **Usage**: Parse and process incoming messages based on message type

#### Message Type Registration

```java
// Option 1: Use example MessageTypes (for testing/demo)
import io.xdag.p2p.example.message.MessageTypes;

// Option 2: Define your own message types (recommended for production)
public enum MyMessageTypes {
    CUSTOM_MSG((byte) 0x10),
    DATA_MSG((byte) 0x11);
    
    private final byte type;
    MyMessageTypes(byte type) { this.type = type; }
    public byte getType() { return type; }
}

public class MyEventHandler extends P2pEventHandler {
    public MyEventHandler() {
        this.messageTypes = new HashSet<>();
        // Register message types your handler wants to receive
        this.messageTypes.add(MyMessageTypes.CUSTOM_MSG.getType());
        this.messageTypes.add(MyMessageTypes.DATA_MSG.getType());
        // Or use example: this.messageTypes.add(MessageTypes.TEST.getType());
    }
}
```

Only messages with registered types will be delivered to this handler.

### Channel

Represents a TCP connection channel for bidirectional message exchange.

#### Key Methods

**send(Message message)**
- **Purpose**: Send a P2P message through the channel
- **Parameters**: 
  - `message`: The Message object to send
- **Returns**: void
- **Behavior**: 
  - Queues message for batch sending
  - Updates last send timestamp
  - Handles errors gracefully

**send(Bytes data)**
- **Purpose**: Send raw bytes data through the channel
- **Parameters**: 
  - `data`: The data as Tuweni Bytes (first byte should be message type)
- **Returns**: void
- **Usage**: Send custom binary data

**close()**
- **Purpose**: Close the channel with default ban time
- **Parameters**: None
- **Returns**: void
- **Behavior**: 
  - Bans peer for default duration
  - Closes underlying TCP connection

**close(long banTime)**
- **Purpose**: Close the channel and ban peer for specified time
- **Parameters**: 
  - `banTime`: Ban duration in milliseconds
- **Returns**: void

**getRemoteAddress()**
- **Purpose**: Get the remote peer's socket address
- **Returns**: `InetSocketAddress`
- **Usage**: Identify the connected peer

#### Channel Properties

- `isActive`: Whether channel is active and ready
- `finishHandshake`: Whether handshake completed
- `nodeId`: Connected node's identifier
- `isTrustPeer`: Whether peer is in trust list
- `lastSendTime`: Timestamp of last message sent
- `layeredStats`: Statistics tracker for this channel

---

## Usage Patterns and Workflows

### Basic Usage Pattern

The standard pattern for using xdagj-p2p follows these steps:

1. **Create Configuration**: Instantiate and configure `P2pConfig`
2. **Create Service**: Instantiate `P2pService` with configuration
3. **Implement Handler**: Extend `P2pEventHandler` for event handling
4. **Register Handler**: Add handler to config via `addP2pEventHandle()`
5. **Start Service**: Call `p2pService.start()`
6. **Use Service**: Send messages, query nodes, manage connections
7. **Stop Service**: Call `p2pService.stop()` on shutdown

### Event-Driven Pattern

xdagj-p2p uses an event-driven architecture:

1. **Connection Events**: `onConnect()` / `onDisconnect()` notify about peer connections
2. **Message Events**: `onMessage()` delivers incoming messages
3. **Asynchronous Operations**: All network operations are non-blocking
4. **Reactive Programming**: Handle events as they occur

### Connection Management Pattern

Connections are managed automatically by the library:

1. **Automatic Discovery**: Kademlia DHT discovers peers automatically
2. **Automatic Connection**: ChannelManager establishes connections to discovered peers
3. **Connection Limits**: Respects min/max connection constraints
4. **Connection Pooling**: Reuses connections efficiently
5. **Automatic Recovery**: Retries failed connections

### Message Sending Pattern

Messages are sent through Channel objects:

1. **Get Channel**: Obtain Channel from `onConnect()` callback or maintain references
2. **Create Message**: Instantiate Message object or prepare Bytes data
3. **Send**: Call `channel.send(message)` or `channel.send(bytes)`
4. **Handle Errors**: Catch exceptions, check channel state

---

## Configuration Reference

### Minimal Configuration

```java
P2pConfig config = new P2pConfig();
config.setPort(16783);
config.setDiscoverEnable(true);
config.setSeedNodes(Arrays.asList(
    new InetSocketAddress("127.0.0.1", 16783)
));
```

### Production Configuration

```java
P2pConfig config = new P2pConfig();
config.setPort(16783);
config.setDiscoverEnable(true);
config.setMinConnections(8);
config.setMaxConnections(30); // Production recommended: 30
config.setSeedNodes(Arrays.asList(
    new InetSocketAddress("seed1.example.com", 16783),
    new InetSocketAddress("seed2.example.com", 16783)
));
config.setDataDir("/var/lib/xdagj-p2p/data");

// CRITICAL: Load persistent node key in production
ECKeyPair nodeKey = loadNodeKeyFromSecureStorage();
config.setNodeKey(nodeKey);
```

### DNS Discovery Configuration

```java
P2pConfig config = new P2pConfig();
config.setDiscoverEnable(false); // Disable DHT if using DNS only
config.setTreeUrls(Arrays.asList(
    "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io"
));
```

### Trusted Nodes Configuration

```java
List<InetAddress> trustNodes = new ArrayList<>();
trustNodes.add(InetAddress.getByName("192.168.1.100"));
trustNodes.add(InetAddress.getByName("10.0.0.50"));
config.setTrustNodes(trustNodes);
```

### Network-Specific Configuration

```java
// XDAG Mainnet
config.setNetworkId((byte) 2);
config.setPort(16783);

// XDAG Testnet
config.setNetworkId((byte) 2);
config.setPort(16783);
config.setNetworkVersion((short) 54321);
```

---

## Complete Code Examples

### Minimal Example

The smallest possible working example:

```java
import io.xdag.p2p.P2pService;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.channel.Channel;
import org.apache.tuweni.bytes.Bytes;
import java.net.InetSocketAddress;
import java.util.Arrays;

public class MinimalExample {
    public static void main(String[] args) {
        // 1. Configure
        P2pConfig config = new P2pConfig();
        config.setPort(16783);
        config.setDiscoverEnable(true);
        config.setSeedNodes(Arrays.asList(
            new InetSocketAddress("127.0.0.1", 16783)
        ));
        
        // 2. Create service
        P2pService p2pService = new P2pService(config);
        
        // 3. Create handler
        P2pEventHandler handler = new P2pEventHandler() {
            @Override
            public void onConnect(Channel channel) {
                System.out.println("Connected: " + channel.getRemoteAddress());
            }
            
            @Override
            public void onMessage(Channel channel, Bytes data) {
                System.out.println("Received message from: " + channel.getRemoteAddress());
            }
        };
        
        // 4. Register handler
        try {
            config.addP2pEventHandle(handler);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        // 5. Start service
        p2pService.start();
        
        // 6. Keep running
        Runtime.getRuntime().addShutdownHook(new Thread(p2pService::stop));
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            p2pService.stop();
        }
    }
}
```

### Event Handler Example

Complete example with custom event handling:

```java
import io.xdag.p2p.P2pService;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.channel.Channel;
import org.apache.tuweni.bytes.Bytes;
import java.net.InetSocketAddress;
import java.util.*;

public class EventHandlerExample {
    private P2pService p2pService;
    private Map<InetSocketAddress, Channel> channels = new HashMap<>();
    
    public void start() {
        // Configure
        P2pConfig config = new P2pConfig();
        config.setPort(16783);
        config.setDiscoverEnable(true);
        config.setSeedNodes(Arrays.asList(
            new InetSocketAddress("127.0.0.1", 16783)
        ));
        
        // Create service
        p2pService = new P2pService(config);
        
        // Create custom handler
        P2pEventHandler handler = new P2pEventHandler() {
            @Override
            public void onConnect(Channel channel) {
                channels.put(channel.getRemoteAddress(), channel);
                System.out.println("Peer connected: " + channel.getRemoteAddress());
                System.out.println("Total connections: " + channels.size());
                
                // Send greeting
                byte[] greeting = "Hello from EventHandlerExample".getBytes();
                Bytes data = Bytes.concatenate(
                    Bytes.of((byte) 0x01), // Message type
                    Bytes.wrap(greeting)
                );
                channel.send(data);
            }
            
            @Override
            public void onDisconnect(Channel channel) {
                channels.remove(channel.getRemoteAddress());
                System.out.println("Peer disconnected: " + channel.getRemoteAddress());
                System.out.println("Remaining connections: " + channels.size());
            }
            
            @Override
            public void onMessage(Channel channel, Bytes data) {
                byte messageType = data.get(0);
                Bytes messageData = data.slice(1);
                
                System.out.println("Received message type: " + messageType);
                System.out.println("From: " + channel.getRemoteAddress());
                System.out.println("Data: " + messageData.toHexString());
                
                // Echo back
                channel.send(data);
            }
        };
        
        // Register handler
        try {
            config.addP2pEventHandle(handler);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        // Start service
        p2pService.start();
    }
    
    public void broadcast(String message) {
        byte[] msgBytes = message.getBytes();
        Bytes data = Bytes.concatenate(
            Bytes.of((byte) 0x01),
            Bytes.wrap(msgBytes)
        );
        
        for (Channel channel : channels.values()) {
            channel.send(data);
        }
    }
    
    public void stop() {
        if (p2pService != null) {
            p2pService.stop();
        }
    }
}
```

### DNS Discovery Example

Example using DNS-based node discovery:

```java
import io.xdag.p2p.P2pService;
import io.xdag.p2p.config.P2pConfig;
import java.util.Arrays;

public class DnsDiscoveryExample {
    public static void main(String[] args) {
        P2pConfig config = new P2pConfig();
        config.setPort(16783);
        config.setDiscoverEnable(false); // Disable DHT, use DNS only
        
        // XDAG Mainnet DNS
        config.setTreeUrls(Arrays.asList(
            "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io"
        ));
        
        P2pService p2pService = new P2pService(config);
        
        // Register minimal handler
        config.addP2pEventHandle(new P2pEventHandler() {
            @Override
            public void onConnect(Channel channel) {
                System.out.println("DNS-discovered peer connected: " + channel.getRemoteAddress());
            }
        });
        
        p2pService.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(p2pService::stop));
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            p2pService.stop();
        }
    }
}
```

### Custom Message Type Example

Example showing how to define and handle custom message types:

```java
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import org.apache.tuweni.bytes.Bytes;
import java.util.HashSet;
import java.util.Set;

// Define message types
enum CustomMessageTypes {
    HELLO((byte) 0x10),
    DATA((byte) 0x11),
    BYE((byte) 0x12);
    
    private final byte type;
    
    CustomMessageTypes(byte type) {
        this.type = type;
    }
    
    public byte getType() {
        return type;
    }
    
    public static CustomMessageTypes fromByte(byte b) {
        for (CustomMessageTypes t : values()) {
            if (t.type == b) return t;
        }
        return null;
    }
}

// Custom message class
class HelloMessage {
    private final String content;
    
    public HelloMessage(String content) {
        this.content = content;
    }
    
    public Bytes toBytes() {
        byte[] contentBytes = content.getBytes();
        return Bytes.concatenate(
            Bytes.of(CustomMessageTypes.HELLO.getType()),
            Bytes.wrap(contentBytes)
        );
    }
    
    public static HelloMessage fromBytes(Bytes data) {
        String content = new String(data.slice(1).toArray());
        return new HelloMessage(content);
    }
    
    public String getContent() {
        return content;
    }
}

// Custom event handler
class CustomMessageHandler extends P2pEventHandler {
    public CustomMessageHandler() {
        this.messageTypes = new HashSet<>();
        this.messageTypes.add(CustomMessageTypes.HELLO.getType());
        this.messageTypes.add(CustomMessageTypes.DATA.getType());
        this.messageTypes.add(CustomMessageTypes.BYE.getType());
    }
    
    @Override
    public void onConnect(Channel channel) {
        // Send hello message on connect
        HelloMessage hello = new HelloMessage("Hello from custom handler!");
        channel.send(hello.toBytes());
    }
    
    @Override
    public void onMessage(Channel channel, Bytes data) {
        byte type = data.get(0);
        Bytes messageData = data.slice(1);
        
        CustomMessageTypes msgType = CustomMessageTypes.fromByte(type);
        if (msgType == null) {
            System.out.println("Unknown message type: " + type);
            return;
        }
        
        switch (msgType) {
            case HELLO:
                HelloMessage hello = HelloMessage.fromBytes(data);
                System.out.println("Received HELLO: " + hello.getContent());
                // Echo back
                channel.send(data);
                break;
                
            case DATA:
                System.out.println("Received DATA: " + messageData.toHexString());
                break;
                
            case BYE:
                System.out.println("Received BYE, closing connection");
                channel.close();
                break;
        }
    }
}
```

---

## Common Tasks and Scenarios

### Starting a P2P Node

```java
P2pConfig config = new P2pConfig();
config.setPort(16783);
config.setDiscoverEnable(true);
config.setSeedNodes(seedNodeList);

P2pService p2pService = new P2pService(config);
config.addP2pEventHandle(new MyEventHandler());
p2pService.start();
```

### Connecting to a Specific Node

```java
InetSocketAddress peerAddress = new InetSocketAddress("192.168.1.100", 16783);
ChannelFuture future = p2pService.connect(peerAddress);
future.addListener((ChannelFuture f) -> {
    if (f.isSuccess()) {
        System.out.println("Connected to " + peerAddress);
    } else {
        System.out.println("Failed to connect: " + f.cause());
    }
});
```

### Sending Messages

```java
// In onConnect callback or maintained channel reference
@Override
public void onConnect(Channel channel) {
    // Send raw bytes
    byte[] message = "Hello".getBytes();
    Bytes data = Bytes.concatenate(
        Bytes.of((byte) 0x01), // Message type
        Bytes.wrap(message)
    );
    channel.send(data);
    
    // Or send Message object (if using library's Message classes)
    // channel.send(message);
}
```

### Receiving Messages

```java
@Override
public void onMessage(Channel channel, Bytes data) {
    byte messageType = data.get(0);
    Bytes messageData = data.slice(1);
    
    // Process based on message type
    switch (messageType) {
        case 0x01:
            // Handle type 0x01
            break;
        // ... other types
    }
}
```

### Monitoring Connection Status

```java
// Get all connectable nodes
List<Node> nodes = p2pService.getConnectableNodes();
System.out.println("Connectable nodes: " + nodes.size());

// Check channel state
if (channel.isActive()) {
    System.out.println("Channel is active");
}
if (channel.isFinishHandshake()) {
    System.out.println("Handshake completed");
}
```

### Graceful Shutdown

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.out.println("Shutting down P2P service...");
    p2pService.stop();
    System.out.println("P2P service stopped");
}));
```

### Maintaining Channel References

```java
public class ChannelManager {
    private final Map<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
    
    @Override
    public void onConnect(Channel channel) {
        channels.put(channel.getRemoteAddress(), channel);
    }
    
    @Override
    public void onDisconnect(Channel channel) {
        channels.remove(channel.getRemoteAddress());
    }
    
    public void broadcast(Bytes data) {
        channels.values().forEach(channel -> {
            if (channel.isActive()) {
                channel.send(data);
            }
        });
    }
}
```

---

## Error Handling and Best Practices

### Exception Types

**P2pException**: Main exception type for P2P-related errors
- `TypeEnum.TYPE_ALREADY_REGISTERED`: Message type already registered
- Other types as defined in `P2pException.TypeEnum`

**DnsException**: DNS-related errors
- Various DNS operation failures

### Error Handling Pattern

```java
try {
    config.addP2pEventHandle(handler);
} catch (P2pException e) {
    if (e.getType() == P2pException.TypeEnum.TYPE_ALREADY_REGISTERED) {
        // Handle duplicate registration
        System.err.println("Message type already registered: " + e.getMessage());
    } else {
        // Handle other P2P errors
        e.printStackTrace();
    }
}

// Network errors are typically handled internally
// Check channel state before sending
if (channel.isActive() && !channel.isDisconnect()) {
    channel.send(data);
} else {
    System.out.println("Channel not ready for sending");
}
```

### Best Practices

1. **Node Key Management**
   - **CRITICAL**: Always load persistent node key in production
   - Never use ephemeral keys in production (they change on restart)
   - Store keys securely (encrypted key store)
   - Example:
   ```java
   // Production: Load from secure storage
   ECKeyPair nodeKey = KeyStore.loadKeyPair("node.key");
   config.setNodeKey(nodeKey);
   
   // Testing only: Generate ephemeral
   config.ensureNodeKey(); // Or config.generateNodeKey()
   ```

2. **Connection Limits**
   - Set `maxConnections` based on server capacity (recommended: 30 for production)
   - Use `maxConnectionsWithSameIp` to prevent single-IP flooding
   - Monitor connection count via `getConnectableNodes()`

3. **Message Type Management**
   - Define message types in an enum
   - Register all handled types in handler constructor
   - Use first byte of message data for type identification
   - Validate message types before processing

4. **Resource Management**
   - Always call `p2pService.stop()` on shutdown
   - Register shutdown hooks for graceful cleanup
   - Close channels properly when done
   - Don't hold channel references indefinitely

5. **Thread Safety**
   - Channel operations are thread-safe
   - Handler callbacks may be called from different threads
   - Use concurrent collections for shared state
   - Synchronize access to handler state if needed

6. **Performance Optimization**
   - Use message batching (handled internally by MessageQueue)
   - Avoid blocking operations in event handlers
   - Keep message processing fast (defer heavy work)
   - Monitor statistics via `channel.getLayeredStats()`

7. **Security Considerations**
   - Validate all incoming messages
   - Set trust nodes only for known-good peers
   - Monitor reputation scores
   - Implement rate limiting for custom protocols
   - Use encrypted communication if needed (application layer)

8. **Testing**
   - Use ephemeral keys for testing
   - Test with multiple nodes locally
   - Verify connection recovery after failures
   - Test message handling edge cases

### Common Pitfalls

1. **Forgetting to register handler**: Handler must be registered before `start()`
2. **Not handling message types**: Only registered types are delivered
3. **Ephemeral keys in production**: Node ID changes on restart
4. **Blocking in handlers**: Causes connection delays
5. **Not checking channel state**: Sending to inactive channels fails silently
6. **Memory leaks**: Holding references to disconnected channels

---

## Additional Resources

- **Source Code**: https://github.com/XDagger/xdagj-p2p
- **User Guide**: [docs/USER_GUIDE.md](USER_GUIDE.md)
- **Examples**: [docs/EXAMPLES.md](EXAMPLES.md)
- **Node Discovery**: [docs/NODE_DISCOVERY.md](NODE_DISCOVERY.md)
- **DNS Configuration**: [docs/DNS_CONFIGURATION.md](DNS_CONFIGURATION.md)
- **Reputation System**: [docs/REPUTATION.md](REPUTATION.md)
- **Performance**: [docs/PERFORMANCE.md](PERFORMANCE.md)

---

## Quick Reference

### Essential Classes
- `P2pService`: Main service
- `P2pConfig`: Configuration
- `P2pEventHandler`: Event handling
- `Channel`: Message channel

### Essential Methods
- `P2pService.start()`: Start service
- `P2pService.stop()`: Stop service
- `Channel.send()`: Send message
- `P2pEventHandler.onMessage()`: Receive message

### Key Concepts
- **Node Discovery**: Kademlia DHT or DNS (EIP-1459)
- **Connection Management**: Automatic via ChannelManager
- **Event-Driven**: Handlers receive callbacks for events
- **Message Types**: First byte identifies message type
- **Node Identity**: Derived from ECKeyPair (160-bit Node ID)

---

*This document is optimized for AI model comprehension. For human-readable documentation, see [USER_GUIDE.md](USER_GUIDE.md) and [EXAMPLES.md](EXAMPLES.md).*

