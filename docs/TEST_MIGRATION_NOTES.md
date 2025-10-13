# Test Migration Notes

## Status: 2025-10-13

### Currently Passing Tests: 214/~300

Current test coverage: 71% (89 classes analyzed)

### Tests Requiring Migration

The following test modules are currently excluded due to API changes from the Protobuf to SimpleCodec migration:

#### 1. Channel Module Tests (2 files - PARTIALLY FIXED)
- `ChannelManagerTest.java` - Missing: `DisconnectCode` enum (now part of message classes)
- `ChannelTest.java` - Missing: Base `Message` class

**Issues**:
- Tests reference `io.xdag.p2p.message.node.Message` (base class) which no longer exists
- Tests reference `io.xdag.p2p.message.node.DisconnectCode` as standalone enum

**Current API**:
- Individual message classes: `HelloMessage`, `PingMessage`, `PongMessage`, etc.
- `DisconnectCode` is now in `io.xdag.p2p.message.node.DisconnectMessage`

#### 2. Handler/Node Module Tests (4 files - NEEDS FIX)
- `ConnPoolHandlerTest.java` - Missing: `ConnPoolHandler` class
- `KeepAliveHandlerTest.java` - Missing: Base `Message` class, `MessageType` enum
- `MessageHandlerInterfaceTest.java` - Missing: Base `Message` class
- `NodeDetectHandlerTest.java` - Missing: `StatusMessage`, `NodeDetectHandler` classes

**Issues**:
- Handler classes may have been refactored or removed
- Need to understand new handler architecture

#### 3. Performance Tests (1 file - NEEDS FIX)
- `P2pPerformanceTest.java` - Missing: `StatusMessage` class

**Issues**:
-  References `io.xdag.p2p.message.node.StatusMessage` which doesn't exist in current codebase
- Line 36, 554: imports and usage of `StatusMessage`
- Line 195: Constructor call `new ChannelManager(config, null, null)` - needs to check correct signature

**Current Signature**:
```java
public ChannelManager(P2pConfig config, NodeManager nodeManager)
```

### Migration Strategy

#### Option A: Quick Fix for Performance Tests
1. Remove `StatusMessage` references from `P2pPerformanceTest.java`
2. Fix `ChannelManager` constructor calls
3. Update test to use existing message types only

#### Option B: Complete Migration (Recommended)
1. Audit all excluded tests and create API mapping document
2. Create helper base class if needed for test compatibility
3. Systematically update each test file
4. Re-enable tests incrementally

#### Option C: Deprecate and Rewrite
1. Keep old tests excluded
2. Write new tests from scratch using current API
3. Ensure feature parity with original tests

### API Changes Summary

#### Removed/Changed:
- `io.xdag.p2p.message.node.Message` (base class) → Individual message classes
- `io.xdag.p2p.message.node.StatusMessage` → Removed/renamed
- `io.xdag.p2p.message.node.DisconnectCode` (standalone) → Part of `DisconnectMessage`
- `ConnPoolHandler` → Possibly refactored
- `NodeDetectHandler` → Possibly refactored

#### Current Message Classes:
- `DisconnectMessage`
- `HandshakeMessage`
- `HelloMessage`
- `InitMessage`
- `PingMessage`
- `PongMessage`
- `WorldMessage`

### Recommendation

For v0.1.2 release:
- Focus on **high-priority features** (reputation persistence, ban enhancements, metrics)
- Keep test migration as **medium priority** task
- Document the test debt clearly
- Plan for comprehensive test migration in v0.2.0

Test coverage is currently healthy at 71% with 214 passing tests. The excluded tests represent additional coverage that would be valuable but not blocking for the next release.

### Action Items

1. ✅ Document test migration requirements (this file)
2. ⏳ Update ROADMAP.md to reflect realistic test migration timeline
3. ⏳ Prioritize feature development over test migration for v0.1.2
4. ⏳ Schedule comprehensive test migration for v0.2.0 or v0.3.0

---

Last Updated: 2025-10-13
Author: Development Team
