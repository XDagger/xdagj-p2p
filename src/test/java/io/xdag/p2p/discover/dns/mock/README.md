# Mock DNS for EIP-1459 Testing

A lightweight, in-memory DNS simulator for testing EIP-1459 DNS-based node discovery without requiring real DNS infrastructure.

## Quick Start

```java
// Enable mock mode
MockableLookUpTxt.setMockMode(true);

// Add DNS records
MockDnsResolver resolver = MockDnsResolver.getInstance();
resolver.addRecord("nodes.example.org", "enrtree-root:v1 e=HASH1 l= seq=1 sig=0x...");

// Query records
TXTRecord record = MockableLookUpTxt.lookUpTxt(p2pConfig, "nodes.example.org");

// Cleanup
resolver.clear();
MockableLookUpTxt.setMockMode(false);
```

## Features

- ✅ **Zero dependencies** - No dnsmasq, BIND, or external DNS servers required
- ✅ **Fast** - In-memory lookups, tests complete in < 1 second
- ✅ **Simple** - 3 lines of code to get started
- ✅ **Thread-safe** - Safe for concurrent tests
- ✅ **EIP-1459 compliant** - Supports root, branch, and node entries
- ✅ **Well-tested** - 9 unit tests with 100% pass rate

## Components

### 1. MockDnsResolver
In-memory TXT record storage with CRUD operations.

**Key Methods:**
- `addRecord(name, content)` - Add a DNS record
- `lookupTxt(name)` - Query a DNS record
- `clear()` - Remove all records

### 2. MockableLookUpTxt
Enhanced DNS lookup utility supporting both mock and real DNS modes.

**Key Methods:**
- `setMockMode(enabled)` - Toggle mock mode
- `lookUpTxt(config, name)` - Query DNS (uses mock if enabled)

### 3. MockDnsTest
Comprehensive test suite with 9 test cases.

## Running Tests

```bash
# Run all mock DNS tests
mvn test -Dtest=MockDnsTest

# Run specific test
mvn test -Dtest=MockDnsTest#testMockDnsResolver_AddAndLookup
```

**Results:**
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.554 s
```

## Documentation

- **Chinese Guide**: [docs/DNS_MOCK_TESTING_GUIDE.md](../../../docs/DNS_MOCK_TESTING_GUIDE.md) - Complete tutorial with examples
- **API Reference**: See JavaDoc in source files
- **EIP-1459 Spec**: https://eips.ethereum.org/EIPS/eip-1459

## Use Cases

**✅ Recommended for:**
- Unit testing DNS discovery logic
- Offline development
- CI/CD pipelines
- Quick prototyping
- Educational demos

**❌ Not recommended for:**
- Production DNS services
- Large-scale load testing (> 100k records)
- Integration tests requiring 100% real DNS

## Comparison

| Feature | Mock DNS | Real DNS (dnsmasq) |
|---------|----------|-------------------|
| Setup | ⭐ Very simple | ⭐⭐⭐⭐ Complex |
| Dependencies | None | dnsmasq + root access |
| Speed | < 1s | 10-30s |
| Realism | Simulated | 100% real |
| Best for | Unit tests | Integration tests |

## Architecture

```
MockDnsResolver (Singleton)
    ↓
ConcurrentHashMap<String, String>
    ↓
In-memory TXT record storage
    ↓
MockableLookUpTxt → Queries mock or real DNS
```

## Example: Testing DNS Tree

```java
@Test
void testDnsTreeDiscovery() {
    MockableLookUpTxt.setMockMode(true);
    MockDnsResolver resolver = MockDnsResolver.getInstance();

    // Add root entry
    resolver.addRecord("test.nodes.example.org",
        "enrtree-root:v1 e=ABC123 l= seq=1 sig=0x...");

    // Add branch entry
    resolver.addRecord("ABC123.test.nodes.example.org",
        "enrtree-branch:DEF456,GHI789");

    // Add nodes entry
    resolver.addRecord("DEF456.test.nodes.example.org",
        "enr:BASE64_ENCODED_NODES");

    // Test discovery
    TXTRecord root = MockableLookUpTxt.lookUpTxt(config, "test.nodes.example.org");
    assertNotNull(root);
    assertTrue(LookUpTxt.joinTXTRecord(root).startsWith("enrtree-root:v1"));
}
```

## License

MIT License - See project root LICENSE file

## Contributing

Issues and pull requests welcome!
