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
package io.xdag.p2p.message.discover;

import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.crypto.keys.PublicKey;
import io.xdag.crypto.keys.Signature;
import io.xdag.crypto.keys.Signer;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.MessageException;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * One discovery datagram: {@code code (1) | signature (65) | body}.
 *
 * <p>The signature is made with the node key over {@code sha256(domain | networkId | code | body)} and the
 * identity of the sender is recovered from it, so a datagram cannot be forged in the name of another node and a
 * datagram of one network is not valid on another. (Until version 2 nothing was signed: anybody could put any
 * node id, address and port into a packet and the receiver believed all of it.) What the signature does not
 * prove is the address a datagram came from - that is what the ping/pong exchange with its echoed hash is for.
 *
 * <p>Only the four Kademlia messages travel in these datagrams.
 */
@Getter
public final class KadPacket {

    private static final byte[] DOMAIN = "xdag-discovery-v2".getBytes(StandardCharsets.US_ASCII);
    public static final int SIGNATURE_LENGTH = 65;
    /** Largest datagram that is sent or accepted: fits the smallest IPv6 path MTU without fragmentation. */
    public static final int MAX_LENGTH = 1280;
    public static final int HEADER_LENGTH = 1 + SIGNATURE_LENGTH;

    private final Message message;
    /** Id of the signer ("0x" + 40 hex digits of its address), as node ids are written in discovery. */
    private final String nodeId;
    /** The signed hash; a pong echoes the hash of the ping it answers. */
    private final Bytes32 hash;

    private KadPacket(Message message, String nodeId, Bytes32 hash) {
        this.message = message;
        this.nodeId = nodeId;
        this.hash = hash;
    }

    /** The hash that is signed for a message: it only depends on the message, so a sender knows it in advance. */
    public static Bytes32 hashOf(Message message, byte networkId) {
        byte[] body = message.getBody() == null ? new byte[0] : message.getBody();
        return HashUtils.sha256(Bytes.concatenate(Bytes.wrap(DOMAIN), Bytes.of(networkId, message.getCode().toByte()),
                Bytes.wrap(body)));
    }

    public static boolean isDiscoveryCode(byte code) {
        MessageCode c = MessageCode.of(code);
        return c == MessageCode.KAD_PING || c == MessageCode.KAD_PONG || c == MessageCode.KAD_FIND_NODE
                || c == MessageCode.KAD_NEIGHBORS;
    }

    /** The datagram for a message, signed with the node key. */
    public static Bytes encode(Message message, byte networkId, ECKeyPair key) {
        byte code = message.getCode().toByte();
        if (!isDiscoveryCode(code)) {
            throw new IllegalArgumentException("not a discovery message: " + message.getCode());
        }
        byte[] body = message.getBody() == null ? new byte[0] : message.getBody();
        Signature signature = Signer.sign(hashOf(message, networkId), key);
        Bytes wire = Bytes.concatenate(Bytes.of(code), signature.encodedBytes(), Bytes.wrap(body));
        if (wire.size() > MAX_LENGTH) {
            throw new IllegalArgumentException("discovery packet too large: " + wire.size());
        }
        return wire;
    }

    /**
     * Parses and verifies a datagram.
     *
     * @throws MessageException if it is not a well-formed, correctly signed discovery message of this network
     */
    public static KadPacket decode(Bytes wire, byte networkId) throws MessageException {
        if (wire == null || wire.size() < HEADER_LENGTH + 1 || wire.size() > MAX_LENGTH) {
            throw new MessageException("Bad discovery packet length");
        }
        byte code = wire.get(0);
        if (!isDiscoveryCode(code)) {
            throw new MessageException("Not a discovery message: " + code);
        }
        Signature signature;
        try {
            signature = Signature.decode(wire.slice(1, SIGNATURE_LENGTH));
        } catch (RuntimeException e) {
            throw new MessageException("Malformed signature", e);
        }
        byte[] body = wire.slice(HEADER_LENGTH).toArray();
        Message message;
        try {
            message = new io.xdag.p2p.message.MessageFactory().create(code, body);
        } catch (MessageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MessageException("Malformed discovery message", e);
        }
        if (message == null) {
            throw new MessageException("Not a discovery message: " + code);
        }
        Bytes32 hash = hashOf(message, networkId);
        PublicKey signer;
        try {
            signer = Signer.recoverPublicKey(hash, signature);
        } catch (RuntimeException e) {
            throw new MessageException("Bad signature", e);
        }
        if (signer == null || !Signer.verify(hash, signature, signer)) {
            throw new MessageException("Bad signature");
        }
        String nodeId = signer.toAddress().toHexString();
        // A recoverable signature always recovers to *some* key; what ties the packet to a node is that the
        // sender named in it is the key that signed it. A packet that was changed on the way, or signed for
        // another network, recovers to a key that does not match the name.
        Node from = fromOf(message);
        if (from == null || !nodeId.equals(from.getId())) {
            throw new MessageException("Sender id does not match the signature");
        }
        return new KadPacket(message, nodeId, hash);
    }

    private static Node fromOf(Message m) {
        if (m instanceof KadPingMessage ping) {
            return ping.getFrom();
        } else if (m instanceof KadPongMessage pong) {
            return pong.getFrom();
        } else if (m instanceof KadFindNodeMessage find) {
            return find.getFrom();
        } else if (m instanceof KadNeighborsMessage neighbors) {
            return neighbors.getFrom();
        }
        return null;
    }
}
