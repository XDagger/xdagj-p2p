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

import static io.xdag.p2p.config.P2pConstant.MESSAGE_QUEUE_SEND_PERIOD;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.message.node.DisconnectMessage;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.channel.Channel;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j
public class MessageQueue {
    private final P2pConfig config;
    private final Channel channel;
    //'8192' is a value obtained from testing experience, not a standard value.Looking forward to optimization.
    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>(8192);
    private final Queue<Message> prioritized = new ConcurrentLinkedQueue<>();
    private ChannelHandlerContext ctx;
    private AtomicBoolean isClosed = new AtomicBoolean(false);
    private Thread timerThread;  // New virtual thread

    public MessageQueue(P2pConfig config, Channel channel) {
        this.config = config;
        this.channel = channel;
    }

    public synchronized void activate(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        // Replace scheduled task with virtual thread
        timerThread = Thread.ofVirtual()
            .name("MessageQueueTimer")
            .start(() -> {
                log.debug("Message queue virtual thread started");
                while (!isClosed.get()) {
                    try {
                        nudgeQueue();
                        Thread.sleep(MESSAGE_QUEUE_SEND_PERIOD);
                    } catch (InterruptedException e) {
                        log.debug("Message queue thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable t) {
                        log.error("Unhandled exception in message queue", t);
                    }
                }
                log.debug("Message queue virtual thread stopped");
            });
    }

    public synchronized void deactivate() {
        isClosed.set(true);  // Signal the thread to stop
        if (timerThread != null && timerThread.isAlive()) {
            timerThread.interrupt();
            try {
                timerThread.join(1000);  // Wait for clean shutdown
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for message queue shutdown");
                Thread.currentThread().interrupt();
            }
        }
    }

    public void disconnect(ReasonCode code) {
        log.debug("Actively closing the connection: reason = {}", code);

        // avoid repeating close requests
        if (isClosed.compareAndSet(false, true)) {
            ctx.writeAndFlush(new DisconnectMessage(code)).addListener((ChannelFutureListener) future -> ctx.close());
        }
    }

    public void sendMessage(Message msg) {
        //when full message queue, whitelist don't need to disconnect.
        if (config.getNetPrioritizedMessages().contains(msg.getCode())) {
            prioritized.add(msg);
        } else {
            try {
                //update to BlockingQueue, capacity 8192
                queue.put(msg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int size() {
        return queue.size() + prioritized.size();
    }

    private void nudgeQueue() {
        //Increase bandwidth consumption of a full used single sync thread to 3 Mbps.
        int n = Math.min(8, size());
        if (n == 0) {
            return;
        }
        // write out n messages
        for (int i = 0; i < n; i++) {
            Message msg = !prioritized.isEmpty() ? prioritized.poll() : queue.poll();

            log.trace("Wiring message: {}", msg);

            // Track network layer send with message size
            // Calculate message size: 1 byte (type) + body size + XdagFrame header (20 bytes)
            int messageSize = 1 + (msg.getBody() != null ? msg.getBody().length : 0) + 20;

            ctx.write(msg).addListener(future -> {
                if (future.isSuccess() && channel != null && channel.getLayeredStats() != null) {
                    // Record successful network layer send
                    channel.getLayeredStats().getNetwork().recordMessageSent(messageSize);
                }
            });
        }
        ctx.flush();
    }

    // Add getters
    public Thread getTimerThread() {
        return timerThread;
    }

    public boolean isClosed() {
        return isClosed.get();
    }
}
