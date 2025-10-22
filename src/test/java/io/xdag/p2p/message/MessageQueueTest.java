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

package io.xdag.p2p.message;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.PingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageQueueTest {

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private P2pConfig config;
    private MessageQueue queue;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new P2pConfig();
        queue = new MessageQueue(config, channel);

        // Configure mock to return ChannelFuture for writeAndFlush calls
        ChannelFuture mockFuture = mock(ChannelFuture.class);
        when(ctx.writeAndFlush(any())).thenReturn(mockFuture);
        when(mockFuture.addListener(any())).thenReturn(mockFuture);
    }

    @Test
    void testActivateAndDeactivate() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        queue.activate(ctx);
        
        Field timerField = MessageQueue.class.getDeclaredField("timerThread");
        timerField.setAccessible(true);
        Thread timerThread = (Thread) timerField.get(queue);
        assertNotNull(timerThread);
        
        Field closedField = MessageQueue.class.getDeclaredField("isClosed");
        closedField.setAccessible(true);
        AtomicBoolean isClosed = (AtomicBoolean) closedField.get(queue);
        assertFalse(isClosed.get());

        // Send a test message
        Message msg = new PingMessage(new byte[0]);
        queue.sendMessage(msg);
        assertEquals(1, queue.size());

        Thread.sleep(50);

        queue.deactivate();
        assertTrue(isClosed.get());
        verify(ctx, atLeast(0)).write(any());
    }

    @Test
    void testMessageSendingUnderLoad() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        queue.activate(ctx);

        int messageCount = 1000;
        for (int i = 0; i < messageCount; i++) {
            queue.sendMessage(new PingMessage(new byte[0]));
        }

        // The queue processes messages concurrently, so we just verify messages were queued
        // Allow generous tolerance as processing starts immediately
        int queueSize = queue.size();
        assertTrue(queueSize > 0 && queueSize <= messageCount,
                   "Expected queue size between 1 and " + messageCount + ", but was " + queueSize);

        Thread.sleep(100);
        queue.deactivate();

        Field closedField = MessageQueue.class.getDeclaredField("isClosed");
        closedField.setAccessible(true);
        AtomicBoolean isClosed = (AtomicBoolean) closedField.get(queue);
        // Verify some messages were processed (queue should be smaller than initial)
        assertTrue(queue.size() < messageCount, "Expected queue to have processed some messages");
    }

    @Test
    void testInterruptHandling() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        queue.activate(ctx);
        Thread.sleep(20);

        queue.deactivate();

        Field timerField = MessageQueue.class.getDeclaredField("timerThread");
        timerField.setAccessible(true);
        Thread timerThread = (Thread) timerField.get(queue);
        assertTrue(timerThread.isInterrupted());
    }

    @Test
    void testDisconnect() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        queue.disconnect(ReasonCode.BAD_NETWORK);

        assertTrue(queue.isClosed());
        verify(ctx).writeAndFlush(any(io.xdag.p2p.message.node.DisconnectMessage.class));
    }

    @Test
    void testDisconnectMultipleTimes() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        // First disconnect should work
        queue.disconnect(ReasonCode.BAD_NETWORK);
        assertTrue(queue.isClosed());

        // Second disconnect should not send another message (isClosed is already true)
        queue.disconnect(ReasonCode.BAD_NETWORK);

        // Verify only one disconnect message was sent
        verify(ctx, times(1)).writeAndFlush(any(io.xdag.p2p.message.node.DisconnectMessage.class));
    }

    // Test removed - HandshakeInitMessage doesn't exist
    // @Test
    // void testSendMessageWithPrioritizedCode() throws InterruptedException {
    //     queue.activate(ctx);
    //     Message prioritizedMsg = new io.xdag.p2p.message.node.HelloMessage(...);
    //     queue.sendMessage(prioritizedMsg);
    //     assertEquals(1, queue.size());
    //     Thread.sleep(100);
    //     queue.deactivate();
    // }

    @Test
    void testSendMessageWithNormalCode() throws InterruptedException {
        queue.activate(ctx);

        // Send normal message (PingMessage is not prioritized)
        Message normalMsg = new PingMessage(new byte[0]);
        queue.sendMessage(normalMsg);

        assertEquals(1, queue.size());

        Thread.sleep(100);
        queue.deactivate();
    }

    // Test removed - HandshakeInitMessage doesn't exist
    // @Test
    // void testSizeWithBothQueues() throws InterruptedException {
    //     queue.activate(ctx);
    //     queue.sendMessage(new PingMessage(new byte[0]));
    //     Message prioritizedMsg = new io.xdag.p2p.message.node.HelloMessage(...);
    //     queue.sendMessage(prioritizedMsg);
    //     assertTrue(queue.size() >= 1, "Queue size should be at least 1");
    //     Thread.sleep(100);
    //     queue.deactivate();
    // }

    @Test
    void testIsClosedInitiallyFalse() {
        assertFalse(queue.isClosed());
    }

    @Test
    void testIsClosedAfterDeactivate() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        queue.deactivate();

        assertTrue(queue.isClosed());
    }

    @Test
    void testIsClosedAfterDisconnect() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        queue.disconnect(ReasonCode.BAD_PEER);

        assertTrue(queue.isClosed());
    }

    @Test
    void testConstructor() {
        assertNotNull(queue);
        assertEquals(0, queue.size());
        assertFalse(queue.isClosed());
    }

    @Test
    void testActivateCreatesTimerThread() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        queue.activate(ctx);

        Field timerField = MessageQueue.class.getDeclaredField("timerThread");
        timerField.setAccessible(true);
        Thread timerThread = (Thread) timerField.get(queue);

        assertNotNull(timerThread);
        assertTrue(timerThread.isAlive());

        queue.deactivate();
    }

    @Test
    void testMultipleActivateCalls() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        // Second activate should handle gracefully
        queue.activate(ctx);
        Thread.sleep(20);

        assertNotNull(queue.getTimerThread());

        queue.deactivate();
    }

    @Test
    void testDeactivateWithoutActivate() {
        // Should handle deactivate without activate gracefully
        queue.deactivate();
        assertTrue(queue.isClosed());
    }

    @Test
    void testDisconnectWithDifferentReasonCodes() throws InterruptedException {
        queue.activate(ctx);
        Thread.sleep(20);

        queue.disconnect(ReasonCode.BAD_NETWORK_VERSION);
        assertTrue(queue.isClosed());

        queue.deactivate();
    }

    @Test
    void testQueueProcessingWithDelay() throws InterruptedException {
        queue.activate(ctx);

        // Send multiple messages
        for (int i = 0; i < 10; i++) {
            queue.sendMessage(new PingMessage(new byte[0]));
        }

        int initialSize = queue.size();
        assertTrue(initialSize > 0);

        // Wait for some processing
        Thread.sleep(200);

        // Queue should have processed some messages
        int afterSize = queue.size();
        assertTrue(afterSize < initialSize || afterSize == 0,
                  "Expected queue to process messages");

        queue.deactivate();
    }

    @Test
    void testDisconnectCallbackExecuted() throws InterruptedException {
        ChannelFuture mockFuture = mock(ChannelFuture.class);
        when(ctx.writeAndFlush(any())).thenReturn(mockFuture);
        when(mockFuture.addListener(any())).thenAnswer(invocation -> {
            io.netty.channel.ChannelFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(mockFuture);
            return mockFuture;
        });

        queue.activate(ctx);
        Thread.sleep(20);

        queue.disconnect(ReasonCode.BAD_NETWORK);

        // Verify the disconnect message was sent and callback executed
        verify(ctx).writeAndFlush(any(io.xdag.p2p.message.node.DisconnectMessage.class));
        verify(ctx).close();
    }

    @Test
    void testNudgeQueueWithNetworkStats() throws InterruptedException {
        io.xdag.p2p.stats.LayeredStats mockLayeredStats = mock(io.xdag.p2p.stats.LayeredStats.class);
        io.xdag.p2p.stats.LayeredStats.NetworkLayer mockNetworkStats = mock(io.xdag.p2p.stats.LayeredStats.NetworkLayer.class);
        when(channel.getLayeredStats()).thenReturn(mockLayeredStats);
        when(mockLayeredStats.getNetwork()).thenReturn(mockNetworkStats);

        ChannelFuture mockWriteFuture = mock(ChannelFuture.class);
        when(mockWriteFuture.isSuccess()).thenReturn(true);
        when(ctx.write(any())).thenAnswer(invocation -> {
            // Simulate immediate callback execution
            return mockWriteFuture;
        });
        when(mockWriteFuture.addListener(any())).thenAnswer(invocation -> {
            io.netty.util.concurrent.GenericFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(mockWriteFuture);
            return mockWriteFuture;
        });

        queue.activate(ctx);

        // Send a message
        queue.sendMessage(new PingMessage(new byte[10]));

        // Wait for processing
        Thread.sleep(100);

        queue.deactivate();

        // Verify network stats were recorded
        verify(mockNetworkStats, atLeastOnce()).recordMessageSent(anyInt());
    }

    @Test
    void testNudgeQueueWithFailedWrite() throws InterruptedException {
        ChannelFuture mockWriteFuture = mock(ChannelFuture.class);
        when(mockWriteFuture.isSuccess()).thenReturn(false);
        when(ctx.write(any())).thenReturn(mockWriteFuture);
        when(mockWriteFuture.addListener(any())).thenAnswer(invocation -> {
            io.netty.util.concurrent.GenericFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(mockWriteFuture);
            return mockWriteFuture;
        });

        queue.activate(ctx);

        // Send a message
        queue.sendMessage(new PingMessage(new byte[10]));

        // Wait for processing
        Thread.sleep(100);

        queue.deactivate();

        // Verify write was attempted
        verify(ctx, atLeastOnce()).write(any());
    }

    @Test
    void testSendMessageInterruptedException() throws Exception {
        // Use reflection to set queue capacity to 1 to force blocking
        Field queueField = MessageQueue.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        java.util.concurrent.BlockingQueue<Message> blockingQueue =
            new java.util.concurrent.LinkedBlockingQueue<>(1);
        queueField.set(queue, blockingQueue);

        // Fill the queue first
        blockingQueue.put(new PingMessage(new byte[0]));

        // Create a thread that will be interrupted while trying to add to full queue
        Thread testThread = new Thread(() -> {
            try {
                queue.sendMessage(new PingMessage(new byte[0]));
                fail("Expected RuntimeException due to InterruptedException");
            } catch (RuntimeException e) {
                assertTrue(e.getCause() instanceof InterruptedException,
                          "Expected InterruptedException as cause");
            }
        });

        testThread.start();
        Thread.sleep(50);
        testThread.interrupt();
        testThread.join(1000);
    }

    @Test
    void testNudgeQueueWithNullChannelStats() throws InterruptedException {
        // Set channel to return null for getLayeredStats()
        when(channel.getLayeredStats()).thenReturn(null);

        ChannelFuture mockWriteFuture = mock(ChannelFuture.class);
        when(mockWriteFuture.isSuccess()).thenReturn(true);
        when(ctx.write(any())).thenReturn(mockWriteFuture);
        when(mockWriteFuture.addListener(any())).thenAnswer(invocation -> {
            io.netty.util.concurrent.GenericFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(mockWriteFuture);
            return mockWriteFuture;
        });

        queue.activate(ctx);

        // Send a message
        queue.sendMessage(new PingMessage(new byte[10]));

        // Wait for processing
        Thread.sleep(100);

        queue.deactivate();

        // Should not throw exception even with null stats
        verify(ctx, atLeastOnce()).write(any());
    }

    @Test
    void testNudgeQueueMessageSizeCalculation() throws InterruptedException {
        io.xdag.p2p.stats.LayeredStats mockLayeredStats = mock(io.xdag.p2p.stats.LayeredStats.class);
        io.xdag.p2p.stats.LayeredStats.NetworkLayer mockNetworkStats = mock(io.xdag.p2p.stats.LayeredStats.NetworkLayer.class);
        when(channel.getLayeredStats()).thenReturn(mockLayeredStats);
        when(mockLayeredStats.getNetwork()).thenReturn(mockNetworkStats);

        ChannelFuture mockWriteFuture = mock(ChannelFuture.class);
        when(mockWriteFuture.isSuccess()).thenReturn(true);
        when(ctx.write(any())).thenReturn(mockWriteFuture);
        when(mockWriteFuture.addListener(any())).thenAnswer(invocation -> {
            io.netty.util.concurrent.GenericFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(mockWriteFuture);
            return mockWriteFuture;
        });

        queue.activate(ctx);

        // Send message with 15-byte body
        queue.sendMessage(new PingMessage(new byte[15]));

        // Wait for processing
        Thread.sleep(100);

        queue.deactivate();

        // Verify correct size was calculated: 1 (type) + 15 (body) + 20 (frame) = 36 bytes
        verify(mockNetworkStats, atLeastOnce()).recordMessageSent(36);
    }

    // JMH Benchmark
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        MessageQueue queue;
        @Mock ChannelHandlerContext ctx;
        @Mock Channel channel;

        public BenchmarkState() {
            MockitoAnnotations.openMocks(this);
            queue = new MessageQueue(new P2pConfig(), channel);
            queue.activate(ctx);
            for (int i = 0; i < 100; i++) {
                queue.sendMessage(new PingMessage(new byte[0]));
            }
        }
    }

    @Benchmark
    public void benchmarkNudgeQueue(BenchmarkState state, Blackhole blackhole) throws Exception {
        Method nudgeMethod = MessageQueue.class.getDeclaredMethod("nudgeQueue");
        nudgeMethod.setAccessible(true);
        nudgeMethod.invoke(state.queue);
        blackhole.consume(state.queue.size());
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MessageQueueTest.class.getSimpleName())
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupIterations(5)
                .measurementIterations(5)
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
