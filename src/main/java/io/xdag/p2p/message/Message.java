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
import org.apache.tuweni.bytes.Bytes;
import io.xdag.p2p.utils.SimpleEncoder;
import io.xdag.p2p.utils.SimpleDecoder;

@Getter
public abstract class Message {

    /**
     * Message code (interface type for extensibility).
     * Can be either framework message code or application layer message code.
     */
    protected final IMessageCode code;

    /**
     * Response message class.
     */
    protected final Class<?> responseMessageClass;

    /**
     * Message body.
     */
    protected byte[] body;

    /**
     * Create a message instance with extensible message code.
     *
     * <p>This constructor accepts {@link IMessageCode} interface, allowing both
     * P2P framework messages and application layer messages to be created.
     *
     * @param code Message code (can be from P2P layer or application layer)
     * @param responseMessageClass Expected response message class
     */
    public Message(IMessageCode code, Class<?> responseMessageClass) {
        this.code = code;
        this.responseMessageClass = responseMessageClass;
        this.body = Bytes.EMPTY.toArray();
    }

    /**
     * Get the message code as byte.
     *
     * @return message code byte (0x00-0xFF)
     */
    public byte getCodeByte() {
        return code.toByte();
    }

    /**
     * Get the message type (code).
     * @return message type
     * @deprecated Use {@link #getCodeByte()} or access {@link #code} directly
     */
    @Deprecated
    public IMessageCode getType() {
        return code;
    }

    /**
     * Check if this message should be logged.
     *
     * <p>By default, only important P2P framework messages are logged.
     * Application layer messages can override this behavior.
     *
     * @return true if should log, false otherwise
     */
    public boolean needToLog() {
        // Only log framework messages by default
        if (!code.isFrameworkMessage()) {
            return false; // Application layer decides logging
        }

        // Log important framework messages
        byte codeByte = code.toByte();
        return codeByte == 0x11 || // HANDSHAKE_INIT
               codeByte == 0x12 || // HANDSHAKE_HELLO
               codeByte == 0x13 || // HANDSHAKE_WORLD
               codeByte == 0x10;   // DISCONNECT
    }

    /**
     * Encodes the message content using custom encoding.
     * This method must be implemented by all message classes to serialize their specific fields.
     * 
     * @param enc SimpleEncoder to write message data to
     */
    public abstract void encode(SimpleEncoder enc);

    /**
     * Decodes a message from raw bytes based on message code.
     * 
     * @param code Message type code
     * @param body Raw message body bytes
     * @return decoded Message instance
     * @throws MessageException if decoding fails
     */
    public static Message decode(MessageCode code, byte[] body) throws MessageException {
        SimpleDecoder dec = new SimpleDecoder(body);
        MessageFactory factory = new MessageFactory();
        return factory.create(code.toByte(), body);
    }

    /**
     * Gets the message body ready for network transmission.
     * 
     * <p>For UDP messages: Returns body with 1-byte message code prefix
     * <p>For TCP messages: Use getBody() directly (Frame handles the message code)
     * 
     * @return message data as Bytes with message code prefix for UDP
     */
    public Bytes getSendData() {
        // Ensure body is up to date
        if (body == null || body.length == 0) {
            SimpleEncoder enc = new SimpleEncoder();
            encode(enc);
            body = enc.toBytes();
        }
        // Prepend 1-byte message code before body for UDP framing
        byte[] payload = body != null ? body : new byte[0];
        byte[] out = new byte[1 + payload.length];
        out[0] = code.toByte();
        System.arraycopy(payload, 0, out, 1, payload.length);
        return Bytes.wrap(out);
    }

    /**
     * Return the message name.
     */
    public String toString() {
        return getClass().getName();
    }
}
