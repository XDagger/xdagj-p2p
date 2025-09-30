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

import io.xdag.p2p.message.node.DisconnectMessage;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.InitMessage;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import io.xdag.p2p.message.node.WorldMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageFactory {
    /**
     * Decode a raw message.
     *
     * @param code
     *            The message code
     * @param body
     *            The message body
     * @return The decoded message, or NULL if the message type is not unknown
     * @throws MessageException
     *             when the encoding is illegal
     */
    public Message create(byte code, byte[] body) throws MessageException {

        MessageCode c = MessageCode.of(code);
        if (c == null) {
            //log.debug("Invalid message code: {}", Hex.encode0x(Bytes.of(code)));
            return null;
        }

        try {
            return switch (c) {
                case HANDSHAKE_INIT -> new InitMessage(body);
                case HANDSHAKE_HELLO -> new HelloMessage(body);
                case HANDSHAKE_WORLD -> new WorldMessage(body);
                case DISCONNECT -> new DisconnectMessage(body);
                case PING -> new PingMessage(body);
                case PONG -> new PongMessage(body);
                case KAD_PING -> new io.xdag.p2p.message.discover.KadPingMessage(body);
                case KAD_PONG -> new io.xdag.p2p.message.discover.KadPongMessage(body);
                case KAD_FIND_NODE -> new io.xdag.p2p.message.discover.KadFindNodeMessage(body);
                case KAD_NEIGHBORS -> new io.xdag.p2p.message.discover.KadNeighborsMessage(body);
                case APP_TEST -> new io.xdag.p2p.example.message.AppTestMessage(body);
            };
        } catch (Exception e) {
            throw new MessageException("Failed to decode message", e);
        }
    }

    public static Message parse(io.xdag.p2p.config.P2pConfig config, org.apache.tuweni.bytes.Bytes encoded) throws MessageException {
        if (encoded == null || encoded.size() < 1) {
            throw new MessageException("Empty UDP packet");
        }
        byte code = encoded.get(0);
        byte[] body = encoded.size() > 1 ? encoded.slice(1).toArray() : new byte[0];
        return new MessageFactory().create(code, body);
    }

}
