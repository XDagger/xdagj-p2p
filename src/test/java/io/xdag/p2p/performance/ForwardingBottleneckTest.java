/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 */
package io.xdag.p2p.performance;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.example.handler.ExampleEventHandler;
import io.xdag.p2p.example.message.TestMessage;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/**
 * Deep dive into forwarding bottleneck
 */
public class ForwardingBottleneckTest {

    @Test
    public void testForwardingOperationsBreakdown() throws Exception {
        int iterations = 10000;

        System.out.println("\n=== Forwarding Operations Breakdown ===");
        System.out.printf("Iterations: %,d%n%n", iterations);

        // Setup
        TestableEventHandler handler = new TestableEventHandler("test-node");
        List<Channel> mockChannels = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Channel mockChannel = mock(Channel.class);
            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 10001 + i);
            when(mockChannel.getInetSocketAddress()).thenReturn(addr);
            mockChannels.add(mockChannel);
            handler.getChannels().put(addr, mockChannel);
        }

        TestMessage originalMsg = createTestMessage(0);

        // Test 1: createForwardCopy()
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            originalMsg.createForwardCopy("test-node");
        }
        long t2 = System.nanoTime();
        double createCopyTime = (t2 - t1) / (double) iterations / 1000.0;

        // Test 2: Bytes.concatenate()
        byte[] testData = originalMsg.getData();
        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Bytes.concatenate(Bytes.of((byte)1), Bytes.wrap(testData));
        }
        long t4 = System.nanoTime();
        double concatenateTime = (t4 - t3) / (double) iterations / 1000.0;

        // Test 3: selectForwardTargets()
        long t5 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            handler.testSelectForwardTargets(null);
        }
        long t6 = System.nanoTime();
        double selectTargetsTime = (t6 - t5) / (double) iterations / 1000.0;

        // Test 4: channel.send() (mocked)
        io.xdag.p2p.example.message.AppTestMessage networkMsg =
                new io.xdag.p2p.example.message.AppTestMessage(testData);
        long t7 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (Channel ch : mockChannels) {
                ch.send(networkMsg);
            }
        }
        long t8 = System.nanoTime();
        double sendTime = (t8 - t7) / (double) iterations / 1000.0;

        // Test 5: Full forward operation (without executor)
        long t9 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            handler.forwardNetworkTestMessageDirect(originalMsg);
        }
        long t10 = System.nanoTime();
        double fullForwardTime = (t10 - t9) / (double) iterations / 1000.0;

        // Results
        System.out.printf("createForwardCopy():     %6.2f μs%n", createCopyTime);
        System.out.printf("Bytes.concatenate():     %6.2f μs%n", concatenateTime);
        System.out.printf("selectForwardTargets():  %6.2f μs%n", selectTargetsTime);
        System.out.printf("channel.send() × 5:      %6.2f μs%n", sendTime);
        System.out.printf("Full forward operation:  %6.2f μs%n", fullForwardTime);

        System.out.println("\nThroughput:");
        System.out.printf("Full forward: %,.0f msg/s%n", 1_000_000.0 / fullForwardTime);

        System.out.println("\n💡 Bottleneck analysis:");
        double[] times = {createCopyTime, concatenateTime, selectTargetsTime, sendTime};
        String[] names = {"createForwardCopy", "Bytes.concatenate", "selectForwardTargets", "channel.send×5"};
        double max = Arrays.stream(times).max().getAsDouble();
        for (int i = 0; i < times.length; i++) {
            if (times[i] == max) {
                System.out.printf("🔴 %s is the bottleneck (%.2f μs, %.1f%% of total)%n",
                        names[i], times[i], (times[i] / fullForwardTime) * 100);
            }
        }
    }

    // Helper class
    private static class TestableEventHandler extends ExampleEventHandler {
        public TestableEventHandler(String nodeId) {
            super(nodeId);
        }

        public List<Channel> testSelectForwardTargets(InetSocketAddress addr) {
            return selectForwardTargets(addr);
        }

        // Direct forwarding without executor
        public void forwardNetworkTestMessageDirect(TestMessage originalMessage) {
            try {
                TestMessage forwardCopy = originalMessage.createForwardCopy(getNodeId());

                if (forwardCopy != null && !forwardCopy.isExpired()) {
                    Bytes appPayload = Bytes.concatenate(
                            Bytes.of((byte)1),
                            Bytes.wrap(forwardCopy.getData())
                    );
                    io.xdag.p2p.example.message.AppTestMessage networkMsg =
                            new io.xdag.p2p.example.message.AppTestMessage(appPayload.toArray());

                    List<Channel> targetChannels = selectForwardTargets(null);

                    for (Channel channel : targetChannels) {
                        try {
                            channel.send(networkMsg);
                        } catch (Exception e) {
                            // Silent
                        }
                    }
                }
            } catch (Exception e) {
                // Silent
            }
        }
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
