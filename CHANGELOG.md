# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Reputation system persistence with automatic saves and backups
  - `ReputationManager` class for disk-based reputation storage
  - Automatic periodic saves every 60 seconds
  - Atomic file operations with `.bak` backup files
  - Time-based reputation decay towards neutral (5 points/day)
  - Thread-safe concurrent operations
- Enhanced ban system with reason codes and graduated durations
  - `BanReason` enum with 13 predefined reasons (minor to critical offenses)
  - `BanInfo` class tracking ban details (reason, count, timestamps)
  - `BanStatistics` class for metrics and reporting
  - Graduated ban durations for repeat offenders (2x per offense, max 30 days)
  - Whitelist support for trusted nodes
  - Rich ban management API (getBanInfo, getAllBannedNodes, etc.)
- Prometheus metrics export with HTTP endpoint
  - `P2pMetrics` class collecting 40+ metrics across 5 categories
  - Connection metrics (active/passive counts, duration, success rate)
  - Message metrics (sent/received by type, errors, size, latency)
  - Node metrics (discovered, banned, reputation distribution)
  - DHT metrics (nodes count, lookup success rate)
  - Performance metrics (throughput, JVM stats)
  - HTTP server exposing /metrics endpoint on configurable port
  - Fully integrated into ChannelManager and KadService
  - Configuration options: `metricsEnabled` and `metricsPort` in P2pConfig
- Data directory configuration in `P2pConfig` (default: "data")
- TEST_MIGRATION_NOTES.md documenting test exclusion reasons

### Changed
- `NodeHandler` now loads/saves reputation scores automatically
- `KadService` manages `ReputationManager` lifecycle and receives P2pMetrics instance
- `ChannelManager` receives P2pMetrics instance and records connection/ban metrics
- `NodeManager` receives P2pMetrics instance and passes it to KadService
- `P2pService` initializes metrics and manages HTTP server lifecycle
- `ChannelManager.banNode()` now uses BanReason enum (old method deprecated)
- Improved code organization with better separation of concerns

### Fixed
- Prometheus CollectorRegistry conflicts in tests resolved with proper cleanup

### Planned
- Re-enable and update channel module tests
- Re-enable and update handler/node module tests
- Re-enable and update performance tests

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
- 518 comprehensive tests with 71% coverage
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
