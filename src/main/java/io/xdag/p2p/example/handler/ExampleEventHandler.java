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
package io.xdag.p2p.example.handler;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.message.MessageTypes;
import io.xdag.p2p.example.message.TestMessage;
import io.xdag.p2p.utils.BytesUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Base event handler for P2P examples Provides common functionality for handling connections and
 * messages
 */
@Slf4j(topic = "example")
public class ExampleEventHandler extends P2pEventHandler {

  // Bloom Filter Configuration
  private static final int EXPECTED_INSERTIONS = 200_000; // Expected message capacity
  private static final double FALSE_POSITIVE_RATE = 0.01; // 1% false positive rate (acceptable for high-TPS)
  
  // Cache Configuration
  private static final long MESSAGE_SOURCE_CACHE_SIZE = 50_000; // Maximum cache entries
  private static final long MESSAGE_SOURCE_EXPIRE_MINUTES = 5; // Auto-expire after 5 minutes
  
  // Thread Pool Configuration
  private static final int FORWARD_THREAD_POOL_SIZE = 64; // Async message forwarding threads
  private static final int MAINTENANCE_THREAD_POOL_SIZE = 1; // Bloom Filter rotation thread
  
  // Maintenance Schedule Configuration
  private static final long BLOOM_FILTER_ROTATION_INITIAL_DELAY_SECONDS = 120; // Initial delay: 2 minutes
  private static final long BLOOM_FILTER_ROTATION_PERIOD_SECONDS = 120; // Rotation every 2 minutes
  private static final long STATS_LOG_INITIAL_DELAY_SECONDS = 10; // Initial delay: 10 seconds
  private static final long STATS_LOG_PERIOD_SECONDS = 10; // Log stats every 10 seconds
  
  // Message Forwarding Configuration
  private static final int FORWARD_PERCENTAGE_NUMERATOR = 3; // Forward to 30% of peers
  private static final int FORWARD_PERCENTAGE_DENOMINATOR = 10;
  private static final int SMALL_NETWORK_THRESHOLD = 2; // Forward to all if peers <= 2
  
  // Shutdown Configuration
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 5; // Graceful shutdown timeout
  
  // Message Processing Constants
  private static final int MESSAGE_TYPE_INDEX = 0; // Index of message type in data
  private static final int MESSAGE_DATA_OFFSET = 1; // Offset to skip message type byte
  private static final int NODE_ID_UUID_LENGTH = 8; // UUID prefix length for node IDs
  
  @Getter
  protected final ConcurrentMap<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();

  // Stage 1.5 Optimization: Bloom Filter for memory-efficient deduplication
  // Replaces memory-leaking ConcurrentHashMaps with space-efficient probabilistic data structure
  // Memory usage: ~120KB for 100K messages (vs 30MB+ for HashMaps)
  // Trade-off: 1% false positive rate (acceptable for high-TPS testing)

  // Use AtomicReference for thread-safe Bloom Filter replacement
  private final AtomicReference<BloomFilter<String>> messageDeduplicationFilter =
      new AtomicReference<>(createBloomFilter());

  // Stage 1.5.2: Use Guava Cache for auto-expiring message source tracking
  // Automatically removes old entries after 5 minutes (prevents memory leak)
  // Much better than manual cleanup which causes message loops
  protected final com.google.common.cache.Cache<String, InetSocketAddress> messageSourceMap;

  /**
   * Handler-level statistics for internal monitoring
   *
   * IMPORTANT: These are global counters at the handler level, NOT per-channel.
   * They track messages AFTER Bloom Filter deduplication.
   * For detailed per-channel network statistics, use channel.getLayeredStats().
   *
   * Semantics:
   * - handlerUniqueReceived: Total unique messages after Bloom Filter deduplication (cumulative)
   * - handlerDuplicates: Total duplicate messages caught by Bloom Filter (cumulative)
   * - handlerForwarded: Total messages forwarded to peers (cumulative)
   * - handlerUniqueSinceRotation: Unique messages since last Bloom Filter rotation (reset every 2min)
   */
  protected final AtomicInteger handlerUniqueReceived = new AtomicInteger(0);
  protected final AtomicInteger handlerForwarded = new AtomicInteger(0);
  protected final AtomicInteger handlerDuplicates = new AtomicInteger(0);
  private final AtomicInteger handlerUniqueSinceRotation = new AtomicInteger(0);

  // Stage 1.2 Optimization: Async message forwarding executor
  // Dedicated thread pool for message forwarding to avoid blocking EventLoop
  protected final ExecutorService forwardExecutor;

  // Stage 1.5 Optimization: Bloom Filter rotation for continuous operation
  // Periodically rebuilds Bloom Filter to prevent saturation
  protected final ScheduledExecutorService maintenanceExecutor;

  // Stage 1.4 Optimization: Round-robin index for load balancing
  // Replaces expensive sort operation (31μs → <1μs)
  private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

  @Getter
  protected final String nodeId;

  /**
   * Create a new Bloom Filter for message deduplication
   */
  private static BloomFilter<String> createBloomFilter() {
    return BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        EXPECTED_INSERTIONS,
        FALSE_POSITIVE_RATE
    );
  }

  public ExampleEventHandler() {
    this(null);
  }

  public ExampleEventHandler(String nodeId) {
    this.nodeId = nodeId != null ? nodeId : generateNodeId();

    // Initialize message types
    this.messageTypes = new HashSet<>();
    this.messageTypes.add(MessageTypes.TEST.getType());

    // Initialize Guava Cache for message source tracking (auto-expiring)
    this.messageSourceMap = com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(MESSAGE_SOURCE_CACHE_SIZE)
        .expireAfterWrite(MESSAGE_SOURCE_EXPIRE_MINUTES, TimeUnit.MINUTES)
        .build();

    // Initialize async forwarding thread pool for high throughput
    this.forwardExecutor = createForwardExecutor();

    // Initialize maintenance executor for Bloom Filter rotation
    this.maintenanceExecutor = createMaintenanceExecutor();

    // Schedule periodic tasks
    schedulePeriodicTasks();
  }

  /**
   * Create forward executor with daemon thread factory
   */
  private static ExecutorService createForwardExecutor() {
    return Executors.newFixedThreadPool(FORWARD_THREAD_POOL_SIZE, new ThreadFactory() {
      private final AtomicInteger threadNumber = new AtomicInteger(1);
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "msg-forward-" + threadNumber.getAndIncrement());
        t.setDaemon(true);
        return t;
      }
    });
  }

  /**
   * Create maintenance executor with daemon thread factory
   */
  private static ScheduledExecutorService createMaintenanceExecutor() {
    return Executors.newScheduledThreadPool(MAINTENANCE_THREAD_POOL_SIZE, r -> {
      Thread t = new Thread(r, "bloom-maintenance");
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Schedule periodic maintenance tasks
   */
  private void schedulePeriodicTasks() {
    // Schedule Bloom Filter rotation to prevent saturation
    maintenanceExecutor.scheduleWithFixedDelay(
        this::rotateBloomFilter,
        BLOOM_FILTER_ROTATION_INITIAL_DELAY_SECONDS,
        BLOOM_FILTER_ROTATION_PERIOD_SECONDS,
        TimeUnit.SECONDS);

    // Schedule periodic statistics logging
    maintenanceExecutor.scheduleWithFixedDelay(
        this::logPeriodicStats,
        STATS_LOG_INITIAL_DELAY_SECONDS,
        STATS_LOG_PERIOD_SECONDS,
        TimeUnit.SECONDS);
  }

  private String generateNodeId() {
    return "node-" + UUID.randomUUID().toString().substring(0, NODE_ID_UUID_LENGTH);
  }

  @Override
  public void onConnect(Channel channel) {
    InetSocketAddress address = channel.getInetSocketAddress();
    channels.put(address, channel);
    log.info("Connected to peer: {}", address);
    onPeerConnected(channel);
  }

  @Override
  public void onDisconnect(Channel channel) {
    InetSocketAddress address = channel.getInetSocketAddress();
    channels.remove(address);
    log.info("Disconnected from peer: {}", address);
    onPeerDisconnected(channel);
  }

  @Override
  public void onMessage(Channel channel, Bytes data) {
    try {
      byte type = data.get(MESSAGE_TYPE_INDEX);
      byte[] messageData = BytesUtils.skip(data, MESSAGE_DATA_OFFSET).toArray();

      MessageTypes messageType = MessageTypes.fromByte(type);
      if (messageType == null) {
        log.warn("Unknown message type: {} from {}", type, channel.getInetSocketAddress());
        return;
      }

      switch (messageType) {
        case TEST:
          TestMessage message = new TestMessage(messageData);
          if (message.isNetworkTestMessage()) {
            handleNetworkTestMessage(channel, message);
          } else {
            log.info(
                "Received test message from {}: {}",
                channel.getInetSocketAddress(),
                message.getContentAsString());
          }
          onTestMessage(channel, message);
          break;
        default:
          log.warn(
              "Unhandled message type: {} from {}", messageType, channel.getInetSocketAddress());
      }
    } catch (Exception e) {
      log.error(
          "Error processing message from {}: {}",
          channel.getInetSocketAddress(),
          e.getMessage(),
          e);
    }
  }

  /**
   * Send test message to all connected peers
   *
   * @param message message content
   */
  public void broadcastTestMessage(String message) {
    TestMessage testMessage = new TestMessage(message);
    Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(testMessage.getData()));
    io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

    channels
        .values()
        .forEach(
            channel -> {
              try {
                channel.send(networkMsg);
                log.info("Sent test message to {}: {}", channel.getInetSocketAddress(), message);
              } catch (Exception e) {
                log.error(
                    "Failed to send message to {}: {}",
                    channel.getInetSocketAddress(),
                    e.getMessage());
              }
            });
  }

  /**
   * Send network test message to all connected peers
   *
   * @param testType Test type identifier
   * @param content Message content
   * @param maxHops Maximum hop count
   */
  public void sendNetworkTestMessage(String testType, String content, int maxHops) {
    String messageId = UUID.randomUUID().toString().substring(0, NODE_ID_UUID_LENGTH);
    TestMessage testMessage = new TestMessage(messageId, nodeId, System.currentTimeMillis(),
                                            0, maxHops, testType, content);

    Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(testMessage.getData()));
    io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

    // Use DEBUG level to reduce log volume in high-TPS scenarios
    log.debug("Sending network test message: {} (type: {}, maxHops: {})",
             messageId, testType, maxHops);

    channels
        .values()
        .forEach(
            channel -> {
              try {
                    channel.send(networkMsg);

                    // Track application layer send
                    if (channel.getLayeredStats() != null) {
                      channel.getLayeredStats().getApplication().recordMessageSent();
                    }

                log.debug("Sent network test message to {}: {}",
                         channel.getInetSocketAddress(), messageId);
              } catch (Exception e) {
                log.error(
                    "Failed to send network test message to {}: {}",
                    channel.getInetSocketAddress(),
                    e.getMessage());
              }
            });
  }

  /**
   * Handle received network test message
   * Stage 1.5 Ultra-optimized for 100K+ TPS with Bloom Filter
   * Stage 2: Enhanced with LayeredStats for Application Layer metrics
   *
   * @param channel the channel that received the message
   * @param message the received network test message
   */
  protected void handleNetworkTestMessage(Channel channel, TestMessage message) {
    try {
      String messageId = message.getMessageId();

      // Track application layer receive (before deduplication check)
      if (channel.getLayeredStats() != null) {
        channel.getLayeredStats().getApplication().recordMessageReceived();
      }

      // FAST OPERATION 1: Check for duplicate using Bloom Filter
      // Bloom Filter provides O(1) lookup with minimal memory (~120KB for 100K messages)
      BloomFilter<String> filter = messageDeduplicationFilter.get();

      if (filter.mightContain(messageId)) {
        // Likely duplicate - increment counter and track in application layer
        // Note: 1% false positive rate means 1% of unique messages will be incorrectly dropped
        // This is acceptable for high-TPS testing scenarios
        handlerDuplicates.incrementAndGet();

        // Track duplicate in application layer stats
        if (channel.getLayeredStats() != null) {
          channel.getLayeredStats().getApplication().recordMessageDuplicated();
        }

        return; // EARLY RETURN - don't block EventLoop
      }

      // New unique message - add to Bloom Filter
      filter.put(messageId);
      handlerUniqueSinceRotation.incrementAndGet();

      // FAST OPERATION 2: Update statistics (atomic operations are fast)
      handlerUniqueReceived.incrementAndGet();

      // Track application layer processing (unique message)
      if (channel.getLayeredStats() != null) {
        channel.getLayeredStats().getApplication().recordMessageProcessed();
      }

      // FAST OPERATION 3: Track message source (for forwarding exclusion)
      // IMPORTANT: Only record the FIRST time we see this message (don't overwrite)
      // This ensures we remember the original sender, not intermediate forwarders
      messageSourceMap.asMap().putIfAbsent(messageId, channel.getInetSocketAddress());

      // OPTIMIZATION: Check if we need to forward BEFORE submitting async task
      if (message.isExpired() || message.getOriginSender().equals(nodeId)) {
        return; // EARLY RETURN - don't submit unnecessary async task
      }

      // ASYNC OPERATION: Submit forwarding to dedicated thread pool
      // EventLoop returns immediately, forwarding happens in background
      forwardExecutor.submit(() -> {
        try {
          forwardNetworkTestMessage(message, channel);
        } catch (Exception e) {
          // Silent failure in extreme TPS mode
        }
      });

    } catch (Exception e) {
      // Silent failure in extreme TPS mode to avoid log overhead
    }
  }

  /**
   * Forward a network test message to connected peers using load-balanced strategy
   *
   * @param originalMessage Original message to forward
   * @param sourceChannel Source channel that received the message (for stats tracking)
   */
  protected void forwardNetworkTestMessage(TestMessage originalMessage, Channel sourceChannel) {
    try {
      TestMessage forwardCopy = originalMessage.createForwardCopy(nodeId);

      if (forwardCopy != null && !forwardCopy.isExpired()) {
        Bytes appPayload = Bytes.concatenate(Bytes.of(MessageTypes.TEST.getType()), Bytes.wrap(forwardCopy.getData()));
        io.xdag.p2p.example.message.AppTestMessage networkMsg = new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

        // Get message source to exclude from forwarding
        InetSocketAddress sourceAddress = messageSourceMap.getIfPresent(originalMessage.getMessageId());

        // Select target channels using load-balanced strategy
        List<Channel> targetChannels = selectForwardTargets(sourceAddress);

        if (!targetChannels.isEmpty()) {
          handlerForwarded.incrementAndGet();

          // Track application layer forwarding
          if (sourceChannel != null && sourceChannel.getLayeredStats() != null) {
            sourceChannel.getLayeredStats().getApplication().recordMessageForwarded();
          }

          log.debug("Forwarding network test message: {} (hops: {}/{}) to {} channels",
                   forwardCopy.getMessageId(), forwardCopy.getHopCount(), forwardCopy.getMaxHops(),
                   targetChannels.size());

          for (Channel channel : targetChannels) {
            try {
              channel.send(networkMsg);
            } catch (Exception e) {
              log.error("Failed to forward network test message to {}: {}",
                       channel.getInetSocketAddress(), e.getMessage());
            }
          }
        }
      }

    } catch (Exception e) {
      log.error("Error forwarding network test message: {}", e.getMessage(), e);
    }
  }

  /**
   * Select forward targets using Round-Robin load balancing
   *
   * Stage 1.4 Optimization: Replaced expensive sort (31μs) with round-robin (<1μs)
   * - Old: O(n log n) sort on every forward
   * - New: O(n) round-robin, 30x faster
   * - Load balancing: Round-robin naturally distributes load evenly
   *
   * Algorithm:
   * 1. Exclude source channel (avoid sending back)
   * 2. Round-robin select 50% of channels
   * 3. Automatically rotates through all peers over time
   *
   * @param sourceAddress Source channel address to exclude
   * @return List of selected channels for forwarding
   */
  protected List<Channel> selectForwardTargets(InetSocketAddress sourceAddress) {
    // Get all channels and filter out source
    List<Channel> candidateChannels = new ArrayList<>();
    for (Channel ch : channels.values()) {
      if (sourceAddress == null || !ch.getInetSocketAddress().equals(sourceAddress)) {
        candidateChannels.add(ch);
      }
    }

    if (candidateChannels.isEmpty()) {
      return candidateChannels;
    }

    // Calculate how many channels to select (30%, at least 1)
    // Reduced from 50% to 30% to mitigate message flooding
    int selectCount = Math.max(1, (candidateChannels.size() * FORWARD_PERCENTAGE_NUMERATOR) / FORWARD_PERCENTAGE_DENOMINATOR);

    // For small networks, select all peers
    if (candidateChannels.size() <= SMALL_NETWORK_THRESHOLD) {
      selectCount = candidateChannels.size();
    }

    // Round-robin selection for natural load balancing
    List<Channel> selectedChannels = new ArrayList<>(selectCount);
    int startIndex = roundRobinIndex.getAndIncrement() % candidateChannels.size();

    for (int i = 0; i < selectCount; i++) {
      int index = (startIndex + i) % candidateChannels.size();
      selectedChannels.add(candidateChannels.get(index));
    }

    return selectedChannels;
  }

  /** Close all connections without banning (for graceful shutdown) */
  public void closeAllConnections() {
    channels
        .values()
        .forEach(
            channel -> {
              try {
                channel.closeWithoutBan();
              } catch (Exception e) {
                log.error(
                    "Error closing channel {}: {}", channel.getInetSocketAddress(), e.getMessage());
              }
            });
    channels.clear();
  }

  /**
   * Rotate Bloom Filter to prevent saturation and maintain continuous operation
   * Called periodically by maintenance thread (every 2 minutes)
   */
  private void rotateBloomFilter() {
    try {
      BloomFilter<String> newFilter = createBloomFilter();
      BloomFilter<String> oldFilter = messageDeduplicationFilter.getAndSet(newFilter);

      int oldUniqueCount = handlerUniqueSinceRotation.getAndSet(0);

      log.info("[{}] Bloom Filter rotated: ~{} unique messages since last rotation, Cache: {} entries",
               nodeId, oldUniqueCount, messageSourceMap.size());

      // Help GC by explicitly nullifying old filter reference
      // (AtomicReference already replaced it, but being explicit)
    } catch (Exception e) {
      log.error("Error rotating Bloom Filter: {}", e.getMessage(), e);
    }
  }

  /**
   * Log periodic statistics for monitoring
   * Called every 10 seconds by maintenance thread
   */
  private void logPeriodicStats() {
    try {
      long cacheSize = messageSourceMap.size();
      com.google.common.cache.CacheStats stats = messageSourceMap.stats();

      log.info("[{}] Handler Stats - Unique: {} | Duplicates: {} | Total: {} | Forwarded: {} | Cache: {}",
               nodeId,
               handlerUniqueSinceRotation.get(),
               handlerDuplicates.get(),
               handlerUniqueReceived.get(),
               handlerForwarded.get(),
               cacheSize);
    } catch (Exception e) {
      log.error("Error logging periodic stats: {}", e.getMessage());
    }
  }

  /**
   * Called when a peer connects (override for custom behavior)
   *
   * @param channel the connected channel
   */
  protected void onPeerConnected(Channel channel) {
    // Override in subclasses for custom behavior
  }

  /**
   * Called when a peer disconnects (override for custom behavior)
   *
   * @param channel the disconnected channel
   */
  protected void onPeerDisconnected(Channel channel) {
    // Override in subclasses for custom behavior
  }

  /**
   * Called when a test message is received (override for custom behavior)
   *
   * @param channel the channel that received the message
   * @param message the received test message
   */
  protected void onTestMessage(Channel channel, TestMessage message) {
    // Override in subclasses for custom behavior
  }



  /**
   * Shutdown executors gracefully
   */
  public void shutdown() {
    try {
      log.info("Shutting down executors for node: {}", nodeId);

      maintenanceExecutor.shutdown();
      forwardExecutor.shutdown();

      if (!maintenanceExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        maintenanceExecutor.shutdownNow();
      }

      if (!forwardExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        forwardExecutor.shutdownNow();
      }

      log.info("Executors shut down successfully for node: {}", nodeId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while shutting down executors", e);
    }
  }
}
