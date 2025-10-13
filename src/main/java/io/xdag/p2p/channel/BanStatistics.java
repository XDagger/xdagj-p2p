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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

/**
 * Statistics about banned nodes.
 */
@Getter
public class BanStatistics {
    private final AtomicInteger totalBans = new AtomicInteger(0);
    private final AtomicInteger activeBans = new AtomicInteger(0);
    private final AtomicLong totalBanDuration = new AtomicLong(0);
    private final Map<BanReason, AtomicInteger> bansByReason = new HashMap<>();

    public BanStatistics() {
        // Initialize counters for each ban reason
        for (BanReason reason : BanReason.values()) {
            bansByReason.put(reason, new AtomicInteger(0));
        }
    }

    /**
     * Record a new ban.
     *
     * @param reason the ban reason
     * @param durationMs the ban duration in milliseconds
     */
    public void recordBan(BanReason reason, long durationMs) {
        totalBans.incrementAndGet();
        activeBans.incrementAndGet();
        totalBanDuration.addAndGet(durationMs);
        bansByReason.get(reason).incrementAndGet();
    }

    /**
     * Record a ban expiry.
     */
    public void recordUnban() {
        activeBans.decrementAndGet();
    }

    /**
     * Get count of bans for a specific reason.
     *
     * @param reason the ban reason
     * @return number of bans for this reason
     */
    public int getBansForReason(BanReason reason) {
        return bansByReason.get(reason).get();
    }

    /**
     * Get average ban duration in milliseconds.
     *
     * @return average duration or 0 if no bans
     */
    public long getAverageBanDuration() {
        int total = totalBans.get();
        return total > 0 ? totalBanDuration.get() / total : 0;
    }

    /**
     * Get statistics summary as a formatted string.
     *
     * @return statistics summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ban Statistics:\n");
        sb.append(String.format("  Total bans: %d\n", totalBans.get()));
        sb.append(String.format("  Active bans: %d\n", activeBans.get()));
        sb.append(String.format("  Average duration: %dms\n", getAverageBanDuration()));
        sb.append("  Bans by reason:\n");

        for (BanReason reason : BanReason.values()) {
            int count = bansByReason.get(reason).get();
            if (count > 0) {
                sb.append(String.format("    %s: %d\n", reason.getDescription(), count));
            }
        }

        return sb.toString();
    }

    /**
     * Reset all statistics.
     */
    public void reset() {
        totalBans.set(0);
        activeBans.set(0);
        totalBanDuration.set(0);
        for (AtomicInteger counter : bansByReason.values()) {
            counter.set(0);
        }
    }
}
