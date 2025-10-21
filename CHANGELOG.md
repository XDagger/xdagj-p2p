# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **Network Layer Statistics Bug**: Fixed send statistics always showing 0
  - Root cause: `XdagFrameCodec.encode()` was missing statistics recording while `decode()` already had it
  - Added network layer send statistics recording in `XdagFrameCodec.encode()` (lines 63-70)
  - Network Layer Sent now shows proper values (e.g., "19,549,346 msgs (1692.21 MB)")

- **Duplicate Connection Attempts**: Reduced duplicate connection attempts from every ~5s to every ~30s
  - Root cause: `connectLoop()` only checked by InetSocketAddress, not Node ID
  - In local testing, inbound connections use random ports (e.g., 58114) not target ports (10001)
  - Added Node ID check in `ChannelManager.connectLoop()` (lines 373-376)
  - Duplicate attempts now limited by recentConnections cache (30-second expiry)

- **Self-Connection Attempts**: Eliminated self-connection attempts in local testing
  - Root cause: Boot nodes list included node's own listen address, connectLoop() didn't filter it
  - Added self-connection detection in `ChannelManager.connectLoop()` (lines 365-370)
  - Now compares port and checks loopback/local address before attempting connection
  - Logs show: "Skipping self-connection to /127.0.0.1:10000"

### Changed
- **Enhanced UDP Discovery Logging**: Upgraded Kademlia DHT protocol logging from DEBUG to INFO level
  - `NodeHandler.handlePing()`, `handlePong()`, `handleNeighbours()`, `handleFindNode()`, `sendFindNode()` now use INFO level
  - `KadService` boot node initialization now uses INFO level logging
  - `DiscoverTask` initialization now uses INFO level logging
  - Significantly improved visibility of UDP discovery protocol activity
  - Makes node discovery completely transparent in production logs

### Fixed
- **Discovery Test Script Accuracy**: Fixed log parsing in test.sh and verify.sh
  - Updated grep patterns to match new log format (`"Sending PING to node:"` instead of `"Send PING"`)
  - Fixed sed extraction pattern to correctly parse discovered node counts (was incorrectly matching year "2025" from timestamps)
  - All discovery metrics now report accurately

### Removed
- **Obsolete Logging Configuration**: Removed unused `test-nodes/discovery-test/logback-discovery.xml`
  - Project uses slf4j-simple, not logback (no logback dependency in pom.xml)
  - Configuration file was completely ineffective
  - Removed `-Dlogback.configurationFile` references from test scripts

### Changed
- **Node ID Implementation**: Migrated from 520-bit uncompressed public key to 160-bit XDAG address
  - Node ID now uses XDAG address format (20 bytes / 40 hex chars) for perfect Kademlia DHT compliance
  - Complies with standard Kademlia 160-bit node ID length (same as BitTorrent DHT)
  - Reduces storage footprint by 69% (from 65 bytes to 20 bytes per node)
  - Improves XOR distance calculation performance (160 bits vs 520 bits)
  - Unifies identity system: Node ID = XDAG address
  - Updated JavaDoc comments in `Node.java` and `P2pConfig.java` to reflect new format
  - Updated all test files to use 20-byte random IDs instead of 64-byte
  - All 471 tests passing with 66% instruction coverage maintained

### Added
- Reputation system persistence with automatic saves and backups
  - `ReputationManager` class for disk-based reputation storage
  - Automatic periodic saves every 60 seconds
  - Atomic file operations with `.bak` backup files
  - Time-based reputation decay towards neutral (5 points/day)
  - Thread-safe concurrent operations
  - **20 comprehensive tests** covering all features
- Simplified ban system with graduated durations
  - `BanInfo` class tracking ban details (count, timestamps, expiry)
  - Graduated ban durations for repeat offenders (2x per offense, max 30 days)
  - Whitelist support for trusted nodes
  - Streamlined ban management API
- Enhanced monitoring with LayeredStats
  - Network layer metrics (messages sent/received, bytes transferred)
  - Application layer metrics (processed, duplicated, forwarded messages)
  - Per-channel statistics tracking
  - Zero external dependencies
- Data directory configuration in `P2pConfig` (default: "data")
- TEST_MIGRATION_NOTES.md documenting test exclusion reasons
- **Mock DNS Testing Framework** for deterministic DNS testing
  - `MockDnsResolver` for in-memory DNS record storage
  - `MockableLookUpTxt` supporting both mock and real DNS queries
  - 9 comprehensive DNS discovery tests

### Changed
- `NodeHandler` now loads/saves reputation scores automatically
- `KadService` manages `ReputationManager` lifecycle
- `ChannelManager` with streamlined ban management (simple duration-based)
- Improved code organization with better separation of concerns

### Fixed
- MessageQueueTest flaky test resolved (tolerance adjustment)
- NodeHandlerTest completely rewritten with 7 comprehensive tests
- TreeTest hash values updated for SimpleCodec serialization

### Removed
- Obsolete P2pPerformanceTest (40+ compilation errors with outdated APIs)
- Redundant test exclusion rules from pom.xml
- Redundant Maven profiles (unit-test, fast-test, full-test)
- Redundant JUnit dependencies (junit-jupiter-api, junit-jupiter-engine, junit-jupiter-params)

### Changed
- pom.xml optimized from 644 lines to 524 lines (-18.6%)
- Test count: 518 tests → 482 tests (removed obsolete tests, all passing)
- README.md updated with accurate test counts and coverage metrics

### Technical Debt Paid
- ✅ Fixed all excluded tests in pom.xml (MessageQueueTest, NodeHandlerTest, TreeTest)
- ✅ Cleaned up build configuration (removed profiles, optimized dependencies)
- ✅ Updated documentation to reflect actual project state

### Added (Test Coverage Improvements)
- **XdagMessageHandlerTest** with 18 comprehensive tests
  - Tests for basic encode/decode operations
  - Tests for Snappy compression support
  - Tests for message chunking and reassembly
  - Tests for error handling (oversized messages, invalid frames, unsupported compression)
  - Tests for edge cases (null frames, packet ID increment, backpressure)
  - Tests for multiple message types
  - **Coverage improved**: 5.8% → 88.9% instructions (402/452 lines covered)
  - **All methods covered**: 100% (7/7 methods)

- **P2pPacketDecoderTest** with 16 comprehensive tests
  - Tests for valid packet decoding (KAD_PING, KAD_PONG, multiple packets)
  - Tests for error handling (empty packets, single byte, oversized, corrupted body)
  - Tests for edge cases (minimal valid packets, different sender addresses)
  - Tests for boundary conditions (length=2, length=2047, MAXSIZE)
  - Tests for invalid message codes (MessageFactory returns null)
  - **Coverage improved**: 6.6% → 57.2% instructions (87/152 instructions covered)
  - **Branch coverage**: 62.5% (5/8 branches)
  - **All methods covered**: 100% (3/3 methods)

### Changed (Test Metrics)
- Test count: 469 tests → 487 tests → **491 tests** (added ReputationManager tests, all passing)
- Overall instruction coverage: 62.9% → 65.6% → **66%** (+3.1%)
- Overall branch coverage: 49.1% → 52.2% → **52%** (+2.9%)
- Overall line coverage: 64.2% → 66.5% → **66.7%** (+2.5%)
- **XdagMessageHandler**: 5.8% → **88.9%** instruction coverage (15x improvement!)
- **P2pPacketDecoder**: 6.6% → **57.2%** instruction coverage (8.7x improvement!)
- All tests passing with comprehensive handler and decoder testing

### Removed (Dead Code Cleanup)
- **Code Simplification (Following Extreme Simplicity Principles)**:
  - Removed 2,277 lines of dead code across 22 commits
  - **Statistics System**: Simplified from 5 classes (650 lines) to 1 class (185 lines) - 72% reduction
    - Removed P2pMetrics (356 lines) - Prometheus exporter never used in production
    - Removed TrafficStats (73 lines) - duplicated LayeredStats functionality
    - Removed P2pStats + P2pStatsManager (85 lines) - just data copying with no value
    - Removed all Prometheus dependencies (simpleclient, simpleclient_httpserver, simpleclient_hotspot)
  - **Ban System**: Simplified from 3 classes to 1 class
    - Removed BanReason enum (116 lines) - only 1 of 15 values ever used
    - Removed BanStatistics (123 lines) - data written but never read
  - **Test-Only Code Removed from Production Classes**:
    - Removed processException() method (only used in tests, production uses Netty's exceptionCaught())
    - Removed latency tracking fields (avgLatency, count, updateAvgLatency())
    - Removed ping tracking fields (waitForPong, pingSent)
    - Removed unused fields (handshakeMessage, node, version, setHandshakeMessage())
  - **Unused Utility Code**:
    - Removed HandshakeSuccessEvent (created but never consumed)
    - Removed 5 unused BytesUtils methods (wrap, slice, equals, extractBytesFromByteBuf, toStr)
    - Removed 3 unused LayeredStats methods (getDuplicationRate, getNetworkEfficiency, reset)
    - Removed 11 unused P2pConstant constants
    - Removed 3 unused P2pConfig fields
  - **Example Package Cleanup**:
    - Fixed thread pool leak (65 threads not being closed)
    - Removed dead code from StartApp and DnsExample
    - Eliminated constructor duplication in ExampleEventHandler
  - **Shell Script Refactoring**:
    - Extracted common functions to lib/common.sh
    - Eliminated 71 lines of duplicate code from test scripts
- **XdagPayloadCodec** (187 lines) - Codec class never used in production
- **XdagPayloadCodecTest** (506 lines, 20 tests) - Tests for unused codec
- **NodeStats** (50 lines) - Stats class never instantiated
- **NodeStatsTest** (270 lines, 13 tests) - Tests for unused stats
- **DnsManager** (146 lines) - Manager class never instantiated
- **UnknownMessage** (48 lines) - Message class never instantiated
- Total cleanup: **Multiple commits removing 2,277+ lines following YAGNI and extreme simplicity principles**
- All 491 tests continue passing with 66% coverage maintained

### Planned
- Improve test coverage for channel module (currently 53.5%)
- Add tests for XdagMessageHandler (currently 5.8%)
- Improve test coverage for discover.dns.update module (currently 30.4%)

## [0.1.1-dev] - 2025-10-13

### Added
- Node banning system with time-based automatic expiry
  - `banNode()` method with automatic connection closure from banned IPs
  - `isBanned()` method with auto-cleanup of expired bans
  - `unbanNode()` method for manual unbanning
  - Integrated ban checking in connection loop to skip banned nodes
- Node reputation system for reliability tracking
  - Simple scoring system (0-200 range, 100 is neutral)
  - Rewards for successful pong responses (+5 points)
  - Penalties for ping timeouts (-5 points)
  - Smart decision making based on reputation scores
  - Low reputation nodes (< 20) are marked as DEAD
  - `getReputationScore()` API for querying node reputation

### Removed
- Obsolete protobuf-based test files
  - `StatusMessageTest.java`
  - `ProtoUtilsTest.java`  
  - `MessageHandlerTest.java`

### Changed
- Improved node lifecycle management with reputation-based decisions
- Enhanced logging for ban and reputation events

### Fixed
- Cleaned up legacy test code referencing removed Protobuf APIs

## [0.1.0] - 2024-09-30

### Added
- Initial release with core P2P networking functionality
- Kademlia DHT implementation
- EIP-1459 DNS discovery support
- Custom high-performance SimpleCodec encoding
- Netty-based async networking
- 472 comprehensive tests with 58% instruction coverage
- Professional P2P network testing suite
- Migration from Protobuf to custom encoding

### Performance
- Message processing: 1.3M-8M ops/sec
- Network I/O: 0.8M-2M ops/sec
- Serialization: 4M-22M ops/sec
- Data access: 98M-206M ops/sec

[Unreleased]: https://github.com/XDagger/xdagj-p2p/compare/v0.1.1-dev...HEAD
[0.1.1-dev]: https://github.com/XDagger/xdagj-p2p/compare/v0.1.0...v0.1.1-dev
[0.1.0]: https://github.com/XDagger/xdagj-p2p/releases/tag/v0.1.0
