/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
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
package io.xdag.p2p.message.node;

import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;

import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.discover.Node;
import io.xdag.crypto.keys.PublicKey;
import io.xdag.crypto.keys.Signature;
import io.xdag.crypto.keys.Signer;
import io.xdag.p2p.Peer;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@Getter
@Setter
public abstract class HandshakeMessage extends Message {

    protected final byte networkId;
    protected final short networkVersion;

    protected final String peerId;
    protected final int port;

    protected final String clientId;
    protected final String[] capabilities;

    protected final long latestBlockNumber;

    protected final byte[] secret;
    protected final long timestamp;
    protected final Signature signature;

    protected PublicKey publicKey;
    protected final boolean isGenerateBlock;
    protected final String nodeTag;

    public HandshakeMessage(
            MessageCode code,
            Class<?> responseMessageClass,
            byte networkId,
            short networkVersion,
            String peerId,
            int port,
            String clientId,
            String[] capabilities,
            long latestBlockNumber,
            byte[] secret,
            ECKeyPair coinbase,
            boolean isGenerateBlock,
            String nodeTag
    ) {
        super(code, responseMessageClass);

        this.networkId = networkId;
        this.networkVersion = networkVersion;
        this.peerId = peerId;
        this.port = port;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.latestBlockNumber = latestBlockNumber;
        this.secret = secret;
        this.timestamp = System.currentTimeMillis();
        this.publicKey = coinbase.getPublicKey();
        this.isGenerateBlock = isGenerateBlock;
        this.nodeTag = nodeTag;

        SimpleEncoder enc = encodeBasicInfo();
        Bytes32 hash = HashUtils.sha256(Bytes.wrap(enc.toBytes()));
        this.signature = Signer.sign(hash, coinbase);

        enc.writeBytes(signature.encodedBytes().toArray());

        this.body = enc.toBytes();
    }

    public HandshakeMessage(MessageCode code, Class<?> responseMessageClass, byte[] body) {
        super(code, responseMessageClass);

        SimpleDecoder dec = new SimpleDecoder(body);
        this.networkId = dec.readByte();
        this.networkVersion = dec.readShort();
        this.peerId = dec.readString();
        this.port = dec.readInt();
        this.clientId = dec.readString();
        List<String> capabilities = new ArrayList<>();
        for (int i = 0, size = dec.readInt(); i < size; i++) {
            capabilities.add(dec.readString());
        }
        this.capabilities = capabilities.toArray(new String[0]);
        this.latestBlockNumber = dec.readLong();
        this.secret = dec.readBytes();
        this.timestamp = dec.readLong();
        this.isGenerateBlock = dec.readBoolean();
        this.nodeTag = dec.readString();

        // Read and validate signature bytes
        byte[] signatureBytes = dec.readBytes();
        if (signatureBytes == null || signatureBytes.length == 0) {
            throw new IllegalArgumentException("Invalid or missing signature in handshake message");
        }
        this.signature = Signature.decode(Bytes.wrap(signatureBytes));
        this.body = body;
    }

    protected SimpleEncoder encodeBasicInfo() {
        SimpleEncoder enc = new SimpleEncoder();

        enc.writeByte(networkId);
        enc.writeShort(networkVersion);
        enc.writeString(peerId);
        enc.writeInt(port);
        enc.writeString(clientId);
        enc.writeInt(capabilities.length);
        for (String capability : capabilities) {
            enc.writeString(capability);
        }
        enc.writeLong(latestBlockNumber);
        enc.writeBytes(secret);
        enc.writeLong(timestamp);
        enc.writeBoolean(isGenerateBlock);
        enc.writeString(nodeTag);

        return enc;
    }

    public boolean validate(P2pConfig config) {
        SimpleEncoder enc = encodeBasicInfo();
        Bytes32 hash = HashUtils.sha256(Bytes.wrap(enc.toBytes()));
        if(publicKey == null && signature !=null) {
            publicKey = Signer.recoverPublicKey(hash, signature);
        }
        if (networkId == config.getNetworkId()
                && networkVersion == config.getNetworkVersion()
                && peerId != null && peerId.length() <= 64
                && port > 0 && port <= 65535
                && clientId != null && clientId.length() < 128
                && latestBlockNumber >= 0
                && secret != null && secret.length == InitMessage.SECRET_LENGTH
                && Math.abs(System.currentTimeMillis() - timestamp) <= config.getNetHandshakeExpiry()
                && signature != null
                && peerId.equals(Base58.encodeCheck(toBytesAddress(publicKey)))) {

            return Signer.verify(hash, signature, publicKey);
        } else {
            return false;
        }
    }

    /**
     * Constructs a Peer object from the handshake info.
     */
    public Peer getPeer(String ip) {
        return new Peer(networkId, networkVersion, peerId, ip, port, clientId, capabilities, latestBlockNumber,
                isGenerateBlock, nodeTag);
    }

    /**
     * Constructs a Node object from the handshake info.
     */
    public Node getFrom() {
        return new Node(peerId, null, null, port);
    }

    /**
     * Get the network version from the handshake message.
     */
    public int getVersion() {
        return networkVersion;
    }

    @Override
    public void encode(SimpleEncoder enc) {
        SimpleEncoder basicEnc = encodeBasicInfo();
        enc.writeBytes(basicEnc.toBytes());
        enc.writeBytes(signature.encodedBytes().toArray());
    }
}
