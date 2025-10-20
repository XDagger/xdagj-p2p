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

import lombok.Getter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Layered P2P network statistics tracker
 *
 * Clearly separates Network Layer (TCP/Netty) and Application Layer (Business Logic) metrics
 * to provide accurate performance insights.
 */
@Getter
public class LayeredStats {

    /**
     * Network Layer Statistics (TCP/Netty level)
     * These counters track actual bytes/messages sent over the wire
     */
    public static class NetworkLayer {
        // Outbound (Send)
        private final AtomicLong messagesSent = new AtomicLong(0);
        private final AtomicLong bytesSent = new AtomicLong(0);

        // Inbound (Receive)
        private final AtomicLong messagesReceived = new AtomicLong(0);
        private final AtomicLong bytesReceived = new AtomicLong(0);

        public void recordMessageSent(int bytes) {
            messagesSent.incrementAndGet();
            bytesSent.addAndGet(bytes);
        }

        public void recordMessageReceived(int bytes) {
            messagesReceived.incrementAndGet();
            bytesReceived.addAndGet(bytes);
        }

        public long getMessagesSent() {
            return messagesSent.get();
        }

        public long getBytesSent() {
            return bytesSent.get();
        }

        public long getMessagesReceived() {
            return messagesReceived.get();
        }

        public long getBytesReceived() {
            return bytesReceived.get();
        }
    }

    /**
     * Application Layer Statistics (Business Logic level)
     * These counters track messages after deduplication and application processing
     */
    public static class ApplicationLayer {
        // Outbound (Send)
        private final AtomicLong messagesSent = new AtomicLong(0);

        // Inbound (Receive)
        private final AtomicLong messagesReceived = new AtomicLong(0);
        private final AtomicLong messagesProcessed = new AtomicLong(0);
        private final AtomicLong messagesDuplicated = new AtomicLong(0);
        private final AtomicLong messagesForwarded = new AtomicLong(0);

        public void recordMessageSent() {
            messagesSent.incrementAndGet();
        }

        public void recordMessageReceived() {
            messagesReceived.incrementAndGet();
        }

        public void recordMessageProcessed() {
            messagesProcessed.incrementAndGet();
        }

        public void recordMessageDuplicated() {
            messagesDuplicated.incrementAndGet();
        }

        public void recordMessageForwarded() {
            messagesForwarded.incrementAndGet();
        }

        public long getMessagesSent() {
            return messagesSent.get();
        }

        public long getMessagesReceived() {
            return messagesReceived.get();
        }

        public long getMessagesProcessed() {
            return messagesProcessed.get();
        }

        public long getMessagesDuplicated() {
            return messagesDuplicated.get();
        }

        public long getMessagesForwarded() {
            return messagesForwarded.get();
        }
    }

    private final NetworkLayer network = new NetworkLayer();
    private final ApplicationLayer application = new ApplicationLayer();

    public NetworkLayer getNetwork() {
        return network;
    }

    public ApplicationLayer getApplication() {
        return application;
    }
}
