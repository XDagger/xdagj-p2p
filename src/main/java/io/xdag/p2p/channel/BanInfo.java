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
package io.xdag.p2p.channel;

import java.net.InetAddress;

/**
 * Information about a banned node. Java record for immutable ban data with automatic getters,
 * equals, hashCode, and toString.
 *
 * @param address the banned IP address
 * @param reason the reason for the ban
 * @param banTimestamp the timestamp when the ban was applied
 * @param banExpiryTimestamp the timestamp when the ban expires
 * @param banCount the number of times this node has been banned
 */
public record BanInfo(
    InetAddress address,
    BanReason reason,
    long banTimestamp,
    long banExpiryTimestamp,
    int banCount) {

    /**
     * Check if the ban is still active.
     *
     * @return true if ban has not expired
     */
    public boolean isActive() {
        return System.currentTimeMillis() < banExpiryTimestamp;
    }

    /**
     * Get remaining ban duration in milliseconds.
     *
     * @return remaining time or 0 if expired
     */
    public long getRemainingTime() {
        long remaining = banExpiryTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Get ban duration in milliseconds.
     *
     * @return total ban duration
     */
    public long getBanDuration() {
        return banExpiryTimestamp - banTimestamp;
    }

    @Override
    public String toString() {
        return String.format("BanInfo{address=%s, reason=%s, count=%d, remaining=%dms}",
                             address, reason.getDescription(), banCount, getRemainingTime());
    }
}
