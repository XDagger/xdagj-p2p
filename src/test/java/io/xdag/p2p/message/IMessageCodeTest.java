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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for IMessageCode interface default methods.
 * Tests framework/application message classification and helper methods.
 */
class IMessageCodeTest {

    /**
     * Test implementation for boundary testing
     */
    static class TestMessageCode implements IMessageCode {
        private final byte code;

        TestMessageCode(int code) {
            this.code = (byte) code;
        }

        @Override
        public byte toByte() {
            return code;
        }
    }

    @Test
    void testToInt_shouldConvertByteToUnsignedInt() {
        // Test positive byte (0x00-0x7F)
        IMessageCode code1 = new TestMessageCode(0x10);
        assertEquals(0x10, code1.toInt());

        // Test negative byte (0x80-0xFF) - should be unsigned
        IMessageCode code2 = new TestMessageCode(0xFF);
        assertEquals(255, code2.toInt());

        // Test zero
        IMessageCode code3 = new TestMessageCode(0x00);
        assertEquals(0, code3.toInt());

        // Test mid-range negative byte
        IMessageCode code4 = new TestMessageCode(0x80);
        assertEquals(128, code4.toInt());
    }

    @Test
    void testIsFrameworkMessage_kadProtocolRange() {
        // KAD protocol: 0x00-0x0F
        IMessageCode kadStart = new TestMessageCode(0x00);
        assertTrue(kadStart.isFrameworkMessage());
        assertFalse(kadStart.isApplicationMessage());

        IMessageCode kadMid = new TestMessageCode(0x08);
        assertTrue(kadMid.isFrameworkMessage());
        assertFalse(kadMid.isApplicationMessage());

        IMessageCode kadEnd = new TestMessageCode(0x0F);
        assertTrue(kadEnd.isFrameworkMessage());
        assertFalse(kadEnd.isApplicationMessage());
    }

    @Test
    void testIsFrameworkMessage_nodeProtocolRange() {
        // Node protocol: 0x10-0x1F
        IMessageCode nodeStart = new TestMessageCode(0x10);
        assertTrue(nodeStart.isFrameworkMessage());
        assertFalse(nodeStart.isApplicationMessage());

        IMessageCode nodeMid = new TestMessageCode(0x18);
        assertTrue(nodeMid.isFrameworkMessage());
        assertFalse(nodeMid.isApplicationMessage());

        IMessageCode nodeEnd = new TestMessageCode(0x1F);
        assertTrue(nodeEnd.isFrameworkMessage());
        assertFalse(nodeEnd.isApplicationMessage());
    }

    @Test
    void testIsApplicationMessage_applicationRange() {
        // Application: 0x20-0xFF
        IMessageCode appStart = new TestMessageCode(0x20);
        assertFalse(appStart.isFrameworkMessage());
        assertTrue(appStart.isApplicationMessage());

        IMessageCode appMid = new TestMessageCode(0x80);
        assertFalse(appMid.isFrameworkMessage());
        assertTrue(appMid.isApplicationMessage());

        IMessageCode appEnd = new TestMessageCode(0xFF);
        assertFalse(appEnd.isFrameworkMessage());
        assertTrue(appEnd.isApplicationMessage());
    }

    @Test
    void testBoundaryConditions_frameworkApplicationBoundary() {
        // Test boundary between framework and application
        IMessageCode lastFramework = new TestMessageCode(0x1F);
        assertTrue(lastFramework.isFrameworkMessage());
        assertFalse(lastFramework.isApplicationMessage());

        IMessageCode firstApplication = new TestMessageCode(0x20);
        assertFalse(firstApplication.isFrameworkMessage());
        assertTrue(firstApplication.isApplicationMessage());
    }

    @Test
    void testBoundaryConditions_kadNodeBoundary() {
        // Test boundary between KAD and Node protocols
        IMessageCode lastKad = new TestMessageCode(0x0F);
        assertTrue(lastKad.isFrameworkMessage());
        assertEquals(0x0F, lastKad.toInt());

        IMessageCode firstNode = new TestMessageCode(0x10);
        assertTrue(firstNode.isFrameworkMessage());
        assertEquals(0x10, firstNode.toInt());
    }

    @Test
    void testMessageCodeEnum_kadProtocol() {
        // Test framework MessageCode enum KAD protocol codes
        assertTrue(MessageCode.KAD_PING.isFrameworkMessage());
        assertFalse(MessageCode.KAD_PING.isApplicationMessage());
        assertEquals(0x00, MessageCode.KAD_PING.toInt());

        assertTrue(MessageCode.KAD_PONG.isFrameworkMessage());
        assertFalse(MessageCode.KAD_PONG.isApplicationMessage());
        assertEquals(0x01, MessageCode.KAD_PONG.toInt());

        assertTrue(MessageCode.KAD_FIND_NODE.isFrameworkMessage());
        assertEquals(0x02, MessageCode.KAD_FIND_NODE.toInt());

        assertTrue(MessageCode.KAD_NEIGHBORS.isFrameworkMessage());
        assertEquals(0x03, MessageCode.KAD_NEIGHBORS.toInt());
    }

    @Test
    void testMessageCodeEnum_nodeProtocol() {
        // Test framework MessageCode enum Node protocol codes
        assertTrue(MessageCode.DISCONNECT.isFrameworkMessage());
        assertFalse(MessageCode.DISCONNECT.isApplicationMessage());
        assertEquals(0x10, MessageCode.DISCONNECT.toInt());

        assertTrue(MessageCode.HANDSHAKE_INIT.isFrameworkMessage());
        assertEquals(0x11, MessageCode.HANDSHAKE_INIT.toInt());

        assertTrue(MessageCode.HANDSHAKE_HELLO.isFrameworkMessage());
        assertEquals(0x12, MessageCode.HANDSHAKE_HELLO.toInt());

        assertTrue(MessageCode.HANDSHAKE_WORLD.isFrameworkMessage());
        assertEquals(0x13, MessageCode.HANDSHAKE_WORLD.toInt());

        assertTrue(MessageCode.PING.isFrameworkMessage());
        assertEquals(0x14, MessageCode.PING.toInt());

        assertTrue(MessageCode.PONG.isFrameworkMessage());
        assertEquals(0x15, MessageCode.PONG.toInt());
    }

    @Test
    void testMessageCodeEnum_applicationProtocol() {
        // Test application layer message code
        assertFalse(MessageCode.APP_TEST.isFrameworkMessage());
        assertTrue(MessageCode.APP_TEST.isApplicationMessage());
        assertEquals(0x20, MessageCode.APP_TEST.toInt());
    }

    @Test
    void testToByteMethod_consistency() {
        // Ensure toByte() and toInt() are consistent
        for (int i = 0; i <= 255; i++) {
            IMessageCode code = new TestMessageCode(i);
            int intValue = code.toInt();
            byte byteValue = code.toByte();

            // toInt() should return unsigned value
            assertEquals(i, intValue);

            // toByte() should match the original byte value
            assertEquals((byte) i, byteValue);

            // Round-trip conversion should be consistent
            assertEquals(intValue, 0xFF & byteValue);
        }
    }

    @Test
    void testNegativeByteHandling() {
        // Test that negative bytes (0x80-0xFF) are handled correctly
        IMessageCode code128 = new TestMessageCode(0x80);
        assertEquals((byte) 0x80, code128.toByte());
        assertEquals(128, code128.toInt());
        assertTrue(code128.isApplicationMessage());

        IMessageCode code255 = new TestMessageCode(0xFF);
        assertEquals((byte) 0xFF, code255.toByte());
        assertEquals(255, code255.toInt());
        assertTrue(code255.isApplicationMessage());

        // Verify negative byte representation
        assertEquals(-128, (byte) 0x80);  // Byte is signed
        assertEquals(128, 0xFF & (byte) 0x80);  // But toInt() returns unsigned
    }

    @Test
    void testAllMessageCodesAreInCorrectRange() {
        // Verify all framework MessageCode enum values are in framework range
        for (MessageCode code : MessageCode.values()) {
            int intValue = code.toInt();
            if (code == MessageCode.APP_TEST) {
                // APP_TEST should be in application range
                assertTrue(intValue >= 0x20, "APP_TEST should be >= 0x20, got: 0x" + Integer.toHexString(intValue));
                assertTrue(code.isApplicationMessage());
            } else {
                // All other framework codes should be in framework range
                assertTrue(intValue <= 0x1F, code + " should be <= 0x1F, got: 0x" + Integer.toHexString(intValue));
                assertTrue(code.isFrameworkMessage());
            }
        }
    }

    @Test
    void testEdgeCases_allPossibleByteValues() {
        // Test all 256 possible byte values
        for (int i = 0; i < 256; i++) {
            IMessageCode code = new TestMessageCode(i);

            // Verify toInt() always returns positive value in range [0, 255]
            int intValue = code.toInt();
            assertTrue(intValue >= 0 && intValue <= 255,
                    "toInt() should be in range [0,255], got: " + intValue);

            // Verify framework vs application classification
            if (i >= 0x00 && i <= 0x1F) {
                assertTrue(code.isFrameworkMessage(),
                        "Code 0x" + Integer.toHexString(i) + " should be framework message");
                assertFalse(code.isApplicationMessage(),
                        "Code 0x" + Integer.toHexString(i) + " should not be application message");
            } else {
                assertFalse(code.isFrameworkMessage(),
                        "Code 0x" + Integer.toHexString(i) + " should not be framework message");
                assertTrue(code.isApplicationMessage(),
                        "Code 0x" + Integer.toHexString(i) + " should be application message");
            }
        }
    }
}
