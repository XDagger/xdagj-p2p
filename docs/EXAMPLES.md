# P2P Example Code

This package contains comprehensive P2P example code with elegant and modular design.

## 📁 Package Structure

```
io.xdag.p2p.example/
├── cli/                    # CLI parsing utilities
│   └── CliConfigParser.java
├── config/                 # Configuration utilities
│   └── ExampleConfig.java
├── handler/                # Event handlers
│   └── ExampleEventHandler.java
├── message/                # Message types
│   ├── MessageTypes.java
│   └── TestMessage.java
├── BasicExample.java       # Basic P2P usage example
├── DnsExample.java         # DNS discovery example
└── StartApp.java           # Command line application
```

## 🎯 Key Features

### 1. **Modular Design**

- **Configuration Management**: `ExampleConfig` provides fluent API for P2P parameter configuration
- **CLI Parsing**: `CliConfigParser` handles command line arguments uniformly
- **Event Handling**: `ExampleEventHandler` provides base event handling functionality
- **Message System**: Unified message types and processing mechanisms

### 2. **Clean Architecture**

- **StartApp**: Concise command-line application (~150 lines)
- **Single Responsibility**: Each class has a clear, focused purpose
- **Graceful Error Handling**: Uses exceptions with proper error messages
- **Separation of Concerns**: Well-organized code structure

### 3. **Developer-Friendly**

- **Builder Pattern**: Intuitive configuration creation
- **Preset Configurations**: Ready-to-use configurations for common scenarios
- **Unified Interface**: Consistent APIs across all examples
- **Comprehensive Logging**: Structured log output with meaningful topics

## 🚀 Usage Examples

### Basic P2P Usage

```java
// Start P2P service (configuration is handled internally)
BasicExample example = new BasicExample();
example.start();

// Or use custom configuration
ExampleConfig config = ExampleConfig.basic()
    .port(16783)
    .networkId(12345)
    .build();
// Note: BasicExample uses internal configuration, 
// for custom config use P2pService directly:
P2pService p2pService = new P2pService(config.toP2pConfig());
```

### DNS Discovery

```java
// DNS sync mode
DnsExample syncExample = new DnsExample(DnsMode.SYNC);
syncExample.start();

// DNS publish mode
DnsExample publishExample = new DnsExample(DnsMode.PUBLISH);
publishExample.start();
```

### Command Line Application

```bash
# Basic startup
java -jar xdagj-p2p-0.1.2-jar-with-dependencies.jar -s 127.0.0.1:16783

# DNS publishing
java -jar xdagj-p2p-0.1.2-jar-with-dependencies.jar \
  -publish \
  --dns-private your-private-key \
  --domain nodes.example.org
```

## 📋 Configuration Options

### ExampleConfig Builder

```java
ExampleConfig.builder()
    .port(16783)                        // Listen port
    .networkId(11111)                   // Network ID
    .discoverEnable(true)               // Enable discovery
    .minConnections(8)                  // Minimum connections
    .minActiveConnections(2)            // Minimum active connections
    .maxConnections(30)                 // Maximum connections (default 30, not 50)
    .maxConnectionsWithSameIp(2)        // Max connections with same IP
    .seedNodes(seedList)                // Seed nodes
    .activeNodes(activeList)            // Active nodes
    .trustNodes(trustList)              // Trust nodes
    .treeUrls(urlList)                  // DNS tree URLs
    .publishConfig(publishConfig)       // DNS publish configuration
    .build();
```

### Preset Configurations

- `ExampleConfig.basic()`: Basic P2P configuration
- `ExampleConfig.dnsSync()`: DNS synchronization configuration
- `ExampleConfig.dnsPublish(...)`: DNS publishing configuration

## 🔧 Extension Guide

### Custom Event Handler

```java
public class MyEventHandler extends ExampleEventHandler {
    public MyEventHandler() {
        // messageTypes is inherited from P2pEventHandler
        this.messageTypes = new HashSet<>();
        this.messageTypes.add(MessageTypes.TEST.getType());
    }

    @Override
    protected void onPeerConnected(Channel channel) {
        log.info("Custom connection handling: {}", channel.getInetSocketAddress());
        // Custom logic
    }
    
    @Override
    protected void onTestMessage(Channel channel, TestMessage message) {
        log.info("Custom message handling: {}", message.getContentAsString());
        // Custom logic
    }
}
```

### Custom Message Types

```java
// 1. Add new type in MessageTypes enum
public enum MessageTypes {
    FIRST((byte) 0x00),
    TEST((byte) 0x01),
    CUSTOM((byte) 0x02),  // New message type
    LAST((byte) 0x8f);
    
    private final byte type;
    
    MessageTypes(byte type) {
        this.type = type;
    }
    
    public byte getType() {
        return type;
    }
}

// 2. Create message class
public class CustomMessage {
    private final byte[] data;
    
    public CustomMessage(String content) {
        this.data = content.getBytes();
    }
    
    public byte[] getData() {
        return data;
    }
}

// 3. Register and handle in event handler
public MyEventHandler() {
    this.messageTypes = new HashSet<>();
    this.messageTypes.add(MessageTypes.TEST.getType());
    this.messageTypes.add(MessageTypes.CUSTOM.getType()); // Register new type
}

@Override
public void onMessage(Channel channel, Bytes data) {
    byte type = data.get(0);
    byte[] messageData = data.slice(1).toArray();
    
    switch (MessageTypes.fromByte(type)) {
        case TEST:
            TestMessage testMessage = new TestMessage(messageData);
            onTestMessage(channel, testMessage);
            break;
        case CUSTOM:
            CustomMessage customMessage = new CustomMessage(new String(messageData));
            // Handle custom message
            break;
        default:
            log.warn("Unknown message type: {}", type);
    }
}
```

## 📝 Best Practices

1. **Use Preset Configurations**: Prefer `ExampleConfig` preset methods for common scenarios
2. **Extend Event Handlers**: Extend `ExampleEventHandler` rather than implementing from scratch
3. **Unified Message Format**: Use `MessageTypes` and standard message classes
4. **Graceful Shutdown**: Use shutdown hooks to ensure proper resource cleanup
5. **Structured Logging**: Use meaningful log topics and formats

This example package demonstrates professional P2P development patterns with clean, maintainable,
and extensible code architecture. 