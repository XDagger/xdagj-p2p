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

/**
 * Message code provider interface for protocol extensibility.
 *
 * <p>This interface allows application layers to define their own message codes
 * while maintaining type safety and preventing conflicts with P2P framework codes.
 *
 * <h3>Reserved Message Code Ranges:</h3>
 * <ul>
 *   <li><b>0x00-0x0F</b>: KAD protocol (xdagj-p2p framework)</li>
 *   <li><b>0x10-0x1F</b>: Node protocol (xdagj-p2p framework)</li>
 *   <li><b>0x20-0xFF</b>: Application layer (available for custom protocols)</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // In application layer (e.g., xdagj)
 * public enum MessageCode implements IMessageCode {
 *     NEW_BLOCK(0x20),      // Application message
 *     SYNC_BLOCK(0x21);     // Application message
 *
 *     private final int code;
 *
 *     MessageCode(int code) {
 *         if (code >= 0x00 && code <= 0x1F) {
 *             throw new IllegalArgumentException("Conflicts with framework range");
 *         }
 *         this.code = code;
 *     }
 *
 *     public byte toByte() {
 *         return (byte) code;
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.5
 */
public interface IMessageCode {

    /**
     * Get the byte representation of this message code.
     *
     * @return message code as byte (0x00-0xFF)
     */
    byte toByte();

    /**
     * Get the integer representation of this message code.
     *
     * @return message code as int (0-255)
     */
    default int toInt() {
        return 0xFF & toByte();
    }

    /**
     * Check if this is a framework message (P2P layer).
     *
     * <p>Framework messages are in the range 0x00-0x1F and are handled
     * by the xdagj-p2p framework (KAD protocol and Node protocol).
     *
     * @return true if this is a framework message (0x00-0x1F),
     *         false if application message (0x20-0xFF)
     */
    default boolean isFrameworkMessage() {
        int code = toInt();
        return code >= 0x00 && code <= 0x1F;
    }

    /**
     * Check if this is an application layer message.
     *
     * <p>Application messages are in the range 0x20-0xFF and are defined
     * by the application using the xdagj-p2p framework.
     *
     * @return true if this is an application message (0x20-0xFF),
     *         false if framework message (0x00-0x1F)
     */
    default boolean isApplicationMessage() {
        return !isFrameworkMessage();
    }
}
