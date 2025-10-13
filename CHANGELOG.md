# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Re-enable and update channel module tests
- Re-enable and update handler/node module tests
- Re-enable and update performance tests
- Enhance reputation system with persistence
- Add metrics and monitoring capabilities

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
