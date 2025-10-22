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

@Getter
public enum MessageCode {

    // =======================================
    // [0x00, 0x0f] Reserved for KAD
    // =======================================
    KAD_PING((byte) 0x00),
    KAD_PONG((byte) 0x01),
    KAD_FIND_NODE((byte) 0x02),
    KAD_NEIGHBORS((byte) 0x03),

    // =======================================
    // [0x10, 0x1f] Reserved for Node
    // =======================================
    DISCONNECT(0x10),
    HANDSHAKE_INIT(0x11),
    HANDSHAKE_HELLO(0x12),
    HANDSHAKE_WORLD(0x13),
    PING(0x14),
    PONG(0x15),

    // =======================================
    // [0x20, 0x2f] Reserved for Example/Application messages
    // =======================================
    APP_TEST(0x20);


    private static final MessageCode[] map = new MessageCode[256];

    static {
        for (MessageCode mc : MessageCode.values()) {
            map[mc.code] = mc;
        }
    }

    public static MessageCode of(int code) {
        return map[0xff & code];
    }

    private final int code;

    MessageCode(int code) {
        this.code = code;
    }

    public byte toByte() {
        return (byte) code;
    }

}
