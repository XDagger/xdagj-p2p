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

import lombok.Getter;

/**
 * P2P framework message codes.
 *
 * <p>These codes are reserved for the xdagj-p2p framework internal use.
 * Application layers should define their own MessageCode enum implementing
 * {@link IMessageCode} and use codes in the range 0x20-0xFF.
 *
 * @see IMessageCode
 */
@Getter
public enum MessageCode implements IMessageCode {

    // =======================================
    // [0x00, 0x0f] Reserved for KAD protocol
    // =======================================
    KAD_PING((byte) 0x00),
    KAD_PONG((byte) 0x01),
    KAD_FIND_NODE((byte) 0x02),
    KAD_NEIGHBORS((byte) 0x03),

    // =======================================
    // [0x10, 0x1f] Reserved for Node protocol
    // =======================================
    DISCONNECT(0x10),
    HANDSHAKE_INIT(0x11),
    HANDSHAKE_HELLO(0x12),
    HANDSHAKE_WORLD(0x13),
    PING(0x14),
    PONG(0x15),

    // =======================================
    // [0x16] APP_TEST - for testing/debugging only
    // NOTE: Moved from 0x20 to avoid conflict with application message range (0x20-0xFF)
    // =======================================
    APP_TEST(0x16);


    private static final MessageCode[] map = new MessageCode[256];

    static {
        for (MessageCode mc : MessageCode.values()) {
            map[mc.code] = mc;
        }
    }

    /**
     * Get MessageCode from byte value.
     *
     * @param code byte code value
     * @return MessageCode or null if not found
     */
    public static MessageCode of(int code) {
        return map[0xff & code];
    }

    private final int code;

    MessageCode(int code) {
        this.code = code;
    }

    /**
     * Get the byte representation of this message code.
     *
     * @return message code as byte
     */
    @Override
    public byte toByte() {
        return (byte) code;
    }
}
