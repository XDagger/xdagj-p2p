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

import lombok.Getter;

/**
 * Reasons for banning a peer node with graduated ban durations.
 *
 * <p>Ban durations follow a graduated approach based on severity:
 * <ul>
 *   <li>Minor offenses: 5-15 minutes</li>
 *   <li>Moderate offenses: 30-60 minutes</li>
 *   <li>Severe offenses: 2-24 hours</li>
 *   <li>Critical offenses: 7+ days</li>
 * </ul>
 */
@Getter
public enum BanReason {

    // Minor offenses (5-15 minutes)
    INVALID_MESSAGE(1, "Invalid message format", 5 * TimeUnit.MINUTE),
    PROTOCOL_VIOLATION(2, "Protocol violation", 10 * TimeUnit.MINUTE),
    HANDSHAKE_TIMEOUT(3, "Handshake timeout", 15 * TimeUnit.MINUTE),

    // Moderate offenses (30-60 minutes)
    TOO_MANY_INVALID_MESSAGES(10, "Too many invalid messages", 30 * TimeUnit.MINUTE),
    INCOMPATIBLE_PROTOCOL(11, "Incompatible protocol version", 30 * TimeUnit.MINUTE),
    NULL_NODE_ID(12, "Null node identity", 45 * TimeUnit.MINUTE),
    DUPLICATE_CONNECTION(13, "Duplicate connection attempt", 1 * TimeUnit.HOUR),

    // Severe offenses (2-24 hours)
    SYNC_ERROR(20, "Blockchain sync error", 2 * TimeUnit.HOUR),
    BAD_BLOCK(21, "Bad block received", 6 * TimeUnit.HOUR),
    MALICIOUS_BEHAVIOR(22, "Malicious behavior detected", 12 * TimeUnit.HOUR),
    EXCESSIVE_RESOURCE_USAGE(23, "Excessive resource usage", 24 * TimeUnit.HOUR),

    // Critical offenses (7+ days)
    ATTACK_DETECTED(30, "Network attack detected", 7 * TimeUnit.DAY),
    SPAM_FLOOD(31, "Message spam/flood", 14 * TimeUnit.DAY),
    IMPERSONATION(32, "Node impersonation attempt", 30 * TimeUnit.DAY),

    // Manual/custom ban
    MANUAL_BAN(99, "Manually banned by administrator", 24 * TimeUnit.HOUR);

    /** Time unit constants for ban durations (in milliseconds) */
    private static final class TimeUnit {
        static final long MINUTE = 60 * 1000L;
        static final long HOUR = 60 * MINUTE;
        static final long DAY = 24 * HOUR;
    }

    private final int code;
    private final String description;
    private final long defaultDurationMs;

    BanReason(int code, String description, long defaultDurationMs) {
        this.code = code;
        this.description = description;
        this.defaultDurationMs = defaultDurationMs;
    }

    /**
     * Get ban reason by code.
     *
     * @param code the reason code
     * @return the BanReason or null if not found
     */
    public static BanReason fromCode(int code) {
        for (BanReason reason : values()) {
            if (reason.code == code) {
                return reason;
            }
        }
        return null;
    }

    /**
     * Check if this is a minor offense.
     *
     * @return true if ban duration is less than 30 minutes
     */
    public boolean isMinor() {
        return defaultDurationMs < 30 * TimeUnit.MINUTE;
    }

    /**
     * Check if this is a severe or critical offense.
     *
     * @return true if ban duration is 2 hours or more
     */
    public boolean isSevere() {
        return defaultDurationMs >= 2 * TimeUnit.HOUR;
    }
}
