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
package io.xdag.p2p.performance;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import io.xdag.p2p.example.message.TestMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Memory leak detection test for message handling
 * Detects unbounded Map growth that causes OOM
 */
public class MemoryLeakDetectionTest {

    private static final int TEST_MESSAGE_COUNT = 100000;

    /**
     * Test: Detect memory leak in messageFirstReceived map
     * Expected: Map grows without bounds, causing OOM
     */
    @Test
    public void testMessageFirstReceivedMapLeak() {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        long memBefore = getUsedMemory();

        System.out.println("=== Memory Leak Detection Test ===");
        System.out.printf("Processing %,d unique messages...%n", TEST_MESSAGE_COUNT);

        for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
            TestMessage message = createUniqueTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, message);

            if (i % 10000 == 0) {
                long memNow = getUsedMemory();
                long memGrowth = memNow - memBefore;
                System.out.printf("Progress: %,6d msgs | Memory: +%,d KB%n",
                        i, memGrowth / 1024);
            }
        }

        long memAfter = getUsedMemory();
        long totalGrowth = memAfter - memBefore;
        double bytesPerMessage = totalGrowth / (double) TEST_MESSAGE_COUNT;

        System.out.println("\n=== Results ===");
        System.out.printf("Total memory growth: %,d KB%n", totalGrowth / 1024);
        System.out.printf("Bytes per message:   %.2f bytes%n", bytesPerMessage);

        // CRITICAL: This should FAIL with current implementation
        // Each message stores: messageId (String ~40 bytes) + Long (8 bytes) + map overhead (~32 bytes) = ~80 bytes
        // Expected growth: 100K * 80 = ~8 MB
        assertTrue(totalGrowth > 5_000_000,
                "Memory should grow significantly (detected growth: " + totalGrowth / 1024 + " KB)");

        System.out.println("\n⚠️  Memory leak confirmed: messageFirstReceived map grows unbounded");
        System.out.println("Recommendation: Use LRU cache with max size limit");
    }

    /**
     * Test: Verify Map size keeps growing
     */
    @Test
    public void testUnboundedMapGrowth() throws Exception {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        System.out.println("\n=== Unbounded Map Growth Test ===");

        for (int batch = 1; batch <= 5; batch++) {
            int batchSize = 20000;
            for (int i = 0; i < batchSize; i++) {
                TestMessage message = createUniqueTestMessage(batch * 100000 + i);
                handler.handleNetworkTestMessage(mockChannel, message);
            }

            // Use reflection to check internal map size
            int mapSize = getMessageMapSize(handler);
            System.out.printf("Batch %d: Map size = %,d entries%n", batch, mapSize);

            // Map should keep growing
            assertEquals(batch * batchSize, mapSize,
                    "Map should contain all unique messages");
        }

        System.out.println("\n⚠️  Map grows without limit - will cause OOM eventually");
    }

    /**
     * Test: Simulate long-running node to detect memory leak
     */
    @Test
    public void testLongRunningMemoryBehavior() throws Exception {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        System.out.println("\n=== Long-Running Simulation (5 minutes of traffic) ===");

        // Simulate 5 minutes at 1000 msg/s = 300K messages
        int totalMessages = 300000;
        int duplicateRate = 50; // 50% duplicates (realistic P2P scenario)

        AtomicLong uniqueCount = new AtomicLong(0);
        AtomicLong duplicateCount = new AtomicLong(0);

        long startMem = getUsedMemory();

        for (int i = 0; i < totalMessages; i++) {
            // Generate some duplicates
            int messageIndex = (i < totalMessages * duplicateRate / 100)
                    ? i / 2  // First 50% messages have duplicates
                    : i;     // Rest are unique

            TestMessage message = createUniqueTestMessage(messageIndex);
            handler.handleNetworkTestMessage(mockChannel, message);

            if (messageIndex == i) {
                uniqueCount.incrementAndGet();
            } else {
                duplicateCount.incrementAndGet();
            }

            if (i % 50000 == 0 && i > 0) {
                long memNow = getUsedMemory();
                int mapSize = getMessageMapSize(handler);
                System.out.printf("Progress: %,6d msgs | Unique: %,6d | Map size: %,6d | Memory: %,d KB%n",
                        i, uniqueCount.get(), mapSize, (memNow - startMem) / 1024);
            }
        }

        long endMem = getUsedMemory();
        int finalMapSize = getMessageMapSize(handler);

        System.out.println("\n=== Final Results ===");
        System.out.printf("Total messages:    %,d%n", totalMessages);
        System.out.printf("Unique messages:   %,d%n", uniqueCount.get());
        System.out.printf("Duplicate messages: %,d%n", duplicateCount.get());
        System.out.printf("Final map size:    %,d%n", finalMapSize);
        System.out.printf("Memory growth:     %,d KB%n", (endMem - startMem) / 1024);

        // Map should equal unique messages
        assertEquals(uniqueCount.get(), finalMapSize,
                "Map size should equal unique message count");

        System.out.println("\n⚠️  In production: This map will grow to millions of entries over days/weeks");
        System.out.println("Recommendation: Implement cache eviction policy");
    }

    /**
     * Test: Measure memory overhead per message
     */
    @Test
    public void testMemoryOverheadPerMessage() {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        System.out.println("\n=== Memory Overhead Analysis ===");

        // Force GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long memBefore = getUsedMemory();

        // Add exactly 10K messages
        int messageCount = 10000;
        for (int i = 0; i < messageCount; i++) {
            TestMessage message = createUniqueTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, message);
        }

        long memAfter = getUsedMemory();
        long growth = memAfter - memBefore;
        double bytesPerEntry = growth / (double) messageCount;

        System.out.printf("Messages:          %,d%n", messageCount);
        System.out.printf("Memory growth:     %,d bytes%n", growth);
        System.out.printf("Bytes per entry:   %.2f bytes%n", bytesPerEntry);

        // Typical overhead: String (40) + Long (16 with object header) + Map entry (32) = ~88 bytes
        assertTrue(bytesPerEntry > 50 && bytesPerEntry < 200,
                String.format("Expected 50-200 bytes per entry, got %.2f", bytesPerEntry));

        System.out.println("\nProjected memory usage:");
        System.out.printf("- 1 million msgs:  %.1f MB%n", bytesPerEntry * 1_000_000 / 1_048_576);
        System.out.printf("- 10 million msgs: %.1f MB%n", bytesPerEntry * 10_000_000 / 1_048_576);
    }

    // Helper methods

    /**
     * Testable subclass that exposes protected methods
     */
    private static class TestableEventHandler extends ExampleEventHandler {
        public TestableEventHandler(String nodeId) {
            super(nodeId);
        }

        @Override
        public void handleNetworkTestMessage(Channel channel, TestMessage message) {
            super.handleNetworkTestMessage(channel, message);
        }
    }

    private Channel createMockChannel() {
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getInetSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 10000));
        return mockChannel;
    }

    private TestMessage createUniqueTestMessage(int index) {
        String messageId = String.format("msg-%08d", index);
        return new TestMessage(
                messageId,
                "sender-node",
                System.currentTimeMillis(),
                0,
                5,
                "latency_test",
                "Test message " + index
        );
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private int getMessageMapSize(ExampleEventHandler handler) {
        try {
            java.lang.reflect.Field field = ExampleEventHandler.class.getDeclaredField("messageFirstReceived");
            field.setAccessible(true);
            java.util.concurrent.ConcurrentMap<?, ?> map =
                    (java.util.concurrent.ConcurrentMap<?, ?>) field.get(handler);
            return map.size();
        } catch (Exception e) {
            throw new RuntimeException("Failed to access messageFirstReceived map", e);
        }
    }
}
