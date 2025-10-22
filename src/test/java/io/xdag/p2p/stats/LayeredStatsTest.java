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
package io.xdag.p2p.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LayeredStatsTest {

    private LayeredStats stats;

    @BeforeEach
    void setUp() {
        stats = new LayeredStats();
    }

    // ==================== Network Layer Tests ====================

    @Test
    void testNetworkLayerInitialState() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        assertEquals(0, network.getMessagesSent(), "Initial messages sent should be 0");
        assertEquals(0, network.getBytesSent(), "Initial bytes sent should be 0");
        assertEquals(0, network.getMessagesReceived(), "Initial messages received should be 0");
        assertEquals(0, network.getBytesReceived(), "Initial bytes received should be 0");
    }

    @Test
    void testNetworkLayerRecordMessageSent() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        network.recordMessageSent(100);

        assertEquals(1, network.getMessagesSent(), "Messages sent should be 1");
        assertEquals(100, network.getBytesSent(), "Bytes sent should be 100");
    }

    @Test
    void testNetworkLayerRecordMultipleMessagesSent() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        network.recordMessageSent(100);
        network.recordMessageSent(200);
        network.recordMessageSent(300);

        assertEquals(3, network.getMessagesSent(), "Messages sent should be 3");
        assertEquals(600, network.getBytesSent(), "Bytes sent should be 600");
    }

    @Test
    void testNetworkLayerRecordMessageReceived() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        network.recordMessageReceived(150);

        assertEquals(1, network.getMessagesReceived(), "Messages received should be 1");
        assertEquals(150, network.getBytesReceived(), "Bytes received should be 150");
    }

    @Test
    void testNetworkLayerRecordMultipleMessagesReceived() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        network.recordMessageReceived(50);
        network.recordMessageReceived(100);
        network.recordMessageReceived(150);

        assertEquals(3, network.getMessagesReceived(), "Messages received should be 3");
        assertEquals(300, network.getBytesReceived(), "Bytes received should be 300");
    }

    @Test
    void testNetworkLayerBidirectionalTraffic() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        // Send some messages
        network.recordMessageSent(100);
        network.recordMessageSent(200);

        // Receive some messages
        network.recordMessageReceived(150);
        network.recordMessageReceived(250);
        network.recordMessageReceived(350);

        assertEquals(2, network.getMessagesSent(), "Messages sent should be 2");
        assertEquals(300, network.getBytesSent(), "Bytes sent should be 300");
        assertEquals(3, network.getMessagesReceived(), "Messages received should be 3");
        assertEquals(750, network.getBytesReceived(), "Bytes received should be 750");
    }

    @Test
    void testNetworkLayerZeroByteMessage() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        network.recordMessageSent(0);
        network.recordMessageReceived(0);

        assertEquals(1, network.getMessagesSent(), "Messages sent should be 1");
        assertEquals(0, network.getBytesSent(), "Bytes sent should be 0");
        assertEquals(1, network.getMessagesReceived(), "Messages received should be 1");
        assertEquals(0, network.getBytesReceived(), "Bytes received should be 0");
    }

    @Test
    void testNetworkLayerLargeByteCount() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        int largeSize = 1_000_000; // 1MB
        network.recordMessageSent(largeSize);
        network.recordMessageReceived(largeSize);

        assertEquals(1, network.getMessagesSent());
        assertEquals(largeSize, network.getBytesSent());
        assertEquals(1, network.getMessagesReceived());
        assertEquals(largeSize, network.getBytesReceived());
    }

    // ==================== Application Layer Tests ====================

    @Test
    void testApplicationLayerInitialState() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        assertEquals(0, app.getMessagesSent(), "Initial messages sent should be 0");
        assertEquals(0, app.getMessagesReceived(), "Initial messages received should be 0");
        assertEquals(0, app.getMessagesProcessed(), "Initial messages processed should be 0");
        assertEquals(0, app.getMessagesDuplicated(), "Initial messages duplicated should be 0");
        assertEquals(0, app.getMessagesForwarded(), "Initial messages forwarded should be 0");
    }

    @Test
    void testApplicationLayerRecordMessageSent() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        app.recordMessageSent();
        app.recordMessageSent();

        assertEquals(2, app.getMessagesSent(), "Messages sent should be 2");
    }

    @Test
    void testApplicationLayerRecordMessageReceived() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        app.recordMessageReceived();
        app.recordMessageReceived();
        app.recordMessageReceived();

        assertEquals(3, app.getMessagesReceived(), "Messages received should be 3");
    }

    @Test
    void testApplicationLayerRecordMessageProcessed() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        app.recordMessageProcessed();
        app.recordMessageProcessed();

        assertEquals(2, app.getMessagesProcessed(), "Messages processed should be 2");
    }

    @Test
    void testApplicationLayerRecordMessageDuplicated() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        app.recordMessageDuplicated();

        assertEquals(1, app.getMessagesDuplicated(), "Messages duplicated should be 1");
    }

    @Test
    void testApplicationLayerRecordMessageForwarded() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        app.recordMessageForwarded();
        app.recordMessageForwarded();
        app.recordMessageForwarded();

        assertEquals(3, app.getMessagesForwarded(), "Messages forwarded should be 3");
    }

    @Test
    void testApplicationLayerCompleteMessageFlow() {
        LayeredStats.ApplicationLayer app = stats.getApplication();

        // Simulate realistic message flow
        app.recordMessageReceived(); // Receive message
        app.recordMessageProcessed(); // Process it

        app.recordMessageReceived(); // Receive another
        app.recordMessageDuplicated(); // It's a duplicate

        app.recordMessageReceived(); // Receive another
        app.recordMessageForwarded(); // Forward it to another node

        app.recordMessageSent(); // Send a new message

        assertEquals(1, app.getMessagesSent(), "Messages sent should be 1");
        assertEquals(3, app.getMessagesReceived(), "Messages received should be 3");
        assertEquals(1, app.getMessagesProcessed(), "Messages processed should be 1");
        assertEquals(1, app.getMessagesDuplicated(), "Messages duplicated should be 1");
        assertEquals(1, app.getMessagesForwarded(), "Messages forwarded should be 1");
    }

    // ==================== Layered Stats Integration Tests ====================

    @Test
    void testBothLayersIndependent() {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        LayeredStats.ApplicationLayer app = stats.getApplication();

        // Network layer: 5 messages sent (1500 bytes)
        for (int i = 0; i < 5; i++) {
            network.recordMessageSent(300);
        }

        // Application layer: 3 messages sent
        for (int i = 0; i < 3; i++) {
            app.recordMessageSent();
        }

        // Verify independence
        assertEquals(5, network.getMessagesSent(), "Network messages sent should be 5");
        assertEquals(1500, network.getBytesSent(), "Network bytes sent should be 1500");
        assertEquals(3, app.getMessagesSent(), "Application messages sent should be 3");
    }

    @Test
    void testRealisticP2pScenario() {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        LayeredStats.ApplicationLayer app = stats.getApplication();

        // Simulate sending 10 application messages
        for (int i = 0; i < 10; i++) {
            app.recordMessageSent();
            network.recordMessageSent(100); // Each message is 100 bytes at network layer
        }

        // Simulate receiving 20 network messages (some duplicates)
        for (int i = 0; i < 20; i++) {
            network.recordMessageReceived(100);
            app.recordMessageReceived();

            if (i < 15) {
                app.recordMessageProcessed(); // 15 unique messages processed
            } else {
                app.recordMessageDuplicated(); // 5 duplicates
            }
        }

        // Some messages forwarded
        for (int i = 0; i < 3; i++) {
            app.recordMessageForwarded();
            network.recordMessageSent(100); // Forwarding sends at network layer
        }

        // Verify network layer
        assertEquals(13, network.getMessagesSent(), "Network sent: 10 app + 3 forward");
        assertEquals(1300, network.getBytesSent(), "Network bytes sent: 13 * 100");
        assertEquals(20, network.getMessagesReceived());
        assertEquals(2000, network.getBytesReceived());

        // Verify application layer
        assertEquals(10, app.getMessagesSent());
        assertEquals(20, app.getMessagesReceived());
        assertEquals(15, app.getMessagesProcessed());
        assertEquals(5, app.getMessagesDuplicated());
        assertEquals(3, app.getMessagesForwarded());
    }

    // ==================== Concurrency Tests ====================

    @Test
    void testNetworkLayerConcurrentSends() throws InterruptedException {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        int threadCount = 10;
        int messagesPerThread = 100;
        int bytesPerMessage = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        network.recordMessageSent(bytesPerMessage);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete");
        executor.shutdown();

        assertEquals(threadCount * messagesPerThread, network.getMessagesSent(),
                "Should have correct message count");
        assertEquals(threadCount * messagesPerThread * bytesPerMessage, network.getBytesSent(),
                "Should have correct byte count");
    }

    @Test
    void testNetworkLayerConcurrentReceives() throws InterruptedException {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        int threadCount = 10;
        int messagesPerThread = 100;
        int bytesPerMessage = 75;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        network.recordMessageReceived(bytesPerMessage);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete");
        executor.shutdown();

        assertEquals(threadCount * messagesPerThread, network.getMessagesReceived());
        assertEquals(threadCount * messagesPerThread * bytesPerMessage, network.getBytesReceived());
    }

    @Test
    void testApplicationLayerConcurrentOperations() throws InterruptedException {
        LayeredStats.ApplicationLayer app = stats.getApplication();
        int threadCount = 5;
        int operationsPerThread = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        app.recordMessageSent();
                        app.recordMessageReceived();
                        app.recordMessageProcessed();
                        app.recordMessageDuplicated();
                        app.recordMessageForwarded();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete");
        executor.shutdown();

        long expectedCount = threadCount * operationsPerThread;
        assertEquals(expectedCount, app.getMessagesSent());
        assertEquals(expectedCount, app.getMessagesReceived());
        assertEquals(expectedCount, app.getMessagesProcessed());
        assertEquals(expectedCount, app.getMessagesDuplicated());
        assertEquals(expectedCount, app.getMessagesForwarded());
    }

    @Test
    void testMixedConcurrentOperations() throws InterruptedException {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        LayeredStats.ApplicationLayer app = stats.getApplication();
        int threadCount = 8;
        int operationsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (threadId % 2 == 0) {
                            // Even threads: network operations
                            network.recordMessageSent(100);
                            network.recordMessageReceived(100);
                        } else {
                            // Odd threads: application operations
                            app.recordMessageSent();
                            app.recordMessageReceived();
                            app.recordMessageProcessed();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete");
        executor.shutdown();

        int evenThreads = threadCount / 2;
        int oddThreads = threadCount - evenThreads;

        assertEquals(evenThreads * operationsPerThread, network.getMessagesSent());
        assertEquals(evenThreads * operationsPerThread, network.getMessagesReceived());
        assertEquals(oddThreads * operationsPerThread, app.getMessagesSent());
        assertEquals(oddThreads * operationsPerThread, app.getMessagesReceived());
        assertEquals(oddThreads * operationsPerThread, app.getMessagesProcessed());
    }

    // ==================== Edge Cases ====================

    @Test
    void testVeryLargeCounters() {
        LayeredStats.NetworkLayer network = stats.getNetwork();

        // Simulate very high traffic
        for (int i = 0; i < 1_000_000; i++) {
            network.recordMessageSent(1000);
        }

        assertEquals(1_000_000, network.getMessagesSent());
        assertEquals(1_000_000_000L, network.getBytesSent());
    }

    @Test
    void testMultipleLayeredStatsInstances() {
        LayeredStats stats1 = new LayeredStats();
        LayeredStats stats2 = new LayeredStats();

        stats1.getNetwork().recordMessageSent(100);
        stats2.getNetwork().recordMessageSent(200);

        stats1.getApplication().recordMessageSent();
        stats2.getApplication().recordMessageSent();
        stats2.getApplication().recordMessageSent();

        // Verify independence
        assertEquals(1, stats1.getNetwork().getMessagesSent());
        assertEquals(100, stats1.getNetwork().getBytesSent());
        assertEquals(1, stats1.getApplication().getMessagesSent());

        assertEquals(1, stats2.getNetwork().getMessagesSent());
        assertEquals(200, stats2.getNetwork().getBytesSent());
        assertEquals(2, stats2.getApplication().getMessagesSent());
    }

    // ==================== Performance Simulation ====================

    @Test
    void testHighThroughputSimulation() {
        LayeredStats.NetworkLayer network = stats.getNetwork();
        LayeredStats.ApplicationLayer app = stats.getApplication();

        // Simulate 1 minute of high throughput
        // Network: 17,433 msg/sec (from performance benchmarks)
        int networkMessagesPerSec = 17_433;
        int durationSec = 1;
        int avgMessageSize = 100;

        for (int i = 0; i < networkMessagesPerSec * durationSec; i++) {
            network.recordMessageSent(avgMessageSize);
        }

        // Application: 3,658 msg/sec (from performance benchmarks)
        int appMessagesPerSec = 3_658;
        for (int i = 0; i < appMessagesPerSec * durationSec; i++) {
            app.recordMessageSent();
        }

        assertEquals(networkMessagesPerSec, network.getMessagesSent());
        assertEquals(networkMessagesPerSec * avgMessageSize, network.getBytesSent());
        assertEquals(appMessagesPerSec, app.getMessagesSent());
    }
}
