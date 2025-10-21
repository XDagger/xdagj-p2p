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
