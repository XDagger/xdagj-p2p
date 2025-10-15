/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 */
package io.xdag.p2p.performance;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import io.xdag.p2p.example.message.TestMessage;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end throughput benchmark
 * Measures actual message processing throughput including ALL layers
 */
public class MessageProcessingBenchmarkTest {

    /**
     * Benchmark: Message handling throughput (single-threaded)
     * This measures the ACTUAL bottleneck
     */
    @Test
    public void testSingleThreadMessageHandlingThroughput() {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        int testMessages = 100000;
        System.out.println("\n=== Single-Thread Message Handling Benchmark ===");
        System.out.printf("Test messages: %,d%n", testMessages);

        // Warmup
        for (int i = 0; i < 1000; i++) {
            TestMessage msg = createTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, msg);
        }

        // Clear state
        handler.resetNetworkTestStatistics();

        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < testMessages; i++) {
            TestMessage msg = createTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, msg);
        }
        long endTime = System.nanoTime();

        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = testMessages / durationSec;

        System.out.printf("Duration: %.3f sec%n", durationSec);
        System.out.printf("Throughput: %,.0f msg/s%n", throughput);
        System.out.printf("Avg latency: %.2f μs%n", (durationSec * 1_000_000) / testMessages);

        // This reveals the REAL bottleneck
        assertTrue(throughput > 1000, "Too slow: " + throughput + " msg/s");
    }

    /**
     * Benchmark: Multi-threaded message handling
     * Simulates concurrent message arrival
     */
    @Test
    public void testMultiThreadMessageHandlingThroughput() throws Exception {
        int threadCount = 8;
        int messagesPerThread = 10000;
        int totalMessages = threadCount * messagesPerThread;

        System.out.println("\n=== Multi-Thread Message Handling Benchmark ===");
        System.out.printf("Threads: %d%n", threadCount);
        System.out.printf("Messages per thread: %,d%n", messagesPerThread);
        System.out.printf("Total messages: %,d%n", totalMessages);

        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong processedCount = new AtomicLong(0);

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        TestMessage msg = createTestMessage(threadId * 1000000 + i);
                        handler.handleNetworkTestMessage(mockChannel, msg);
                        processedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.nanoTime();

        executor.shutdown();

        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = totalMessages / durationSec;

        System.out.printf("Duration: %.3f sec%n", durationSec);
        System.out.printf("Throughput: %,.0f msg/s%n", throughput);
        System.out.printf("Processed: %,d messages%n", processedCount.get());

        assertEquals(totalMessages, processedCount.get());
    }

    /**
     * Benchmark: Identify which operation is slow in handleNetworkTestMessage
     */
    @Test
    public void testMessageHandlingBreakdown() {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        int iterations = 10000;

        // Test 1: putIfAbsent overhead
        long t1 = System.nanoTime();
        ConcurrentHashMap<String, Long> map = new ConcurrentHashMap<>();
        for (int i = 0; i < iterations; i++) {
            String key = "msg-" + i;
            map.putIfAbsent(key, System.currentTimeMillis());
        }
        long t2 = System.nanoTime();
        double putIfAbsentTime = (t2 - t1) / (double) iterations / 1000.0;

        // Test 2: messageSourceMap.put overhead
        long t3 = System.nanoTime();
        ConcurrentHashMap<String, InetSocketAddress> sourceMap = new ConcurrentHashMap<>();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 10000);
        for (int i = 0; i < iterations; i++) {
            String key = "msg-" + i;
            sourceMap.put(key, addr);
        }
        long t4 = System.nanoTime();
        double putTime = (t4 - t3) / (double) iterations / 1000.0;

        // Test 3: Atomic increment
        long t5 = System.nanoTime();
        AtomicLong counter = new AtomicLong(0);
        for (int i = 0; i < iterations; i++) {
            counter.incrementAndGet();
        }
        long t6 = System.nanoTime();
        double incrementTime = (t6 - t5) / (double) iterations / 1000.0;

        // Test 4: Full message handling
        handler.resetNetworkTestStatistics();
        long t7 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            TestMessage msg = createTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, msg);
        }
        long t8 = System.nanoTime();
        double fullHandlingTime = (t8 - t7) / (double) iterations / 1000.0;

        System.out.println("\n=== Message Handling Breakdown ===");
        System.out.printf("Iterations: %,d%n", iterations);
        System.out.printf("putIfAbsent:        %.2f μs%n", putIfAbsentTime);
        System.out.printf("sourceMap.put:      %.2f μs%n", putTime);
        System.out.printf("atomic increment:   %.2f μs%n", incrementTime);
        System.out.printf("Full handling:      %.2f μs%n", fullHandlingTime);
        System.out.printf("Unaccounted:        %.2f μs%n",
                fullHandlingTime - putIfAbsentTime - putTime - incrementTime);

        System.out.println("\nThroughput:");
        System.out.printf("Full handling: %,.0f msg/s%n", 1_000_000.0 / fullHandlingTime);
    }

    /**
     * Benchmark: Async forwarding executor overhead
     */
    @Test
    public void testAsyncForwardingOverhead() throws Exception {
        TestableEventHandler handler = new TestableEventHandler("test-node");
        Channel mockChannel = createMockChannel();

        // Add some peer channels for forwarding
        for (int i = 0; i < 5; i++) {
            Channel peer = mock(Channel.class);
            when(peer.getInetSocketAddress()).thenReturn(
                    new InetSocketAddress("127.0.0.1", 10001 + i));
            handler.getChannels().put(peer.getInetSocketAddress(), peer);
        }

        int testMessages = 10000;

        System.out.println("\n=== Async Forwarding Overhead Test ===");
        System.out.printf("Peer channels: %d%n", handler.getChannels().size());

        long startTime = System.nanoTime();
        for (int i = 0; i < testMessages; i++) {
            TestMessage msg = createTestMessage(i);
            handler.handleNetworkTestMessage(mockChannel, msg);
        }

        // Wait for async forwarding to complete
        Thread.sleep(1000);

        long endTime = System.nanoTime();

        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = testMessages / durationSec;

        System.out.printf("Duration: %.3f sec (including 1s wait)%n", durationSec);
        System.out.printf("Throughput: %,.0f msg/s%n", throughput);

        System.out.println("\n⚠️  If throughput is low, async forwarding is the bottleneck");
    }

    // Helper classes and methods

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
        when(mockChannel.getInetSocketAddress()).thenReturn(
                new InetSocketAddress("127.0.0.1", 10000));
        return mockChannel;
    }

    private TestMessage createTestMessage(int index) {
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
}
