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
package io.xdag.p2p;

import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a peer node in the XDAG network.
 * Uses custom encoding via SimpleEncoder/SimpleDecoder for efficient serialization.
 */
@Getter
public class Peer {

    // Network instance this peer belongs to
    private final byte networkId;
    
    // Protocol version used by this peer
    private final short networkVersion;
    
    // Unique identifier for this peer
    private final String peerId;
    
    // IP address of the peer
    private final String ip;
    
    // Port number used by the peer
    private final int port;
    
    // Client software identifier
    private final String clientId;
    
    // Supported capabilities/features
    private final String[] capabilities;
    
    // The Latest block number known by this peer
    @Setter
    private long latestBlockNumber;
    
    // Network latency to this peer in milliseconds
    @Setter
    private long latency;

    private final boolean isGenerateBlock;

    private final String nodeTag;

    /**
     * Creates a new Peer instance
     *
     * @param networkId Network Id
     * @param networkVersion Protocol version
     * @param peerId Unique peer identifier
     * @param ip IP address
     * @param port Port number
     * @param clientId Client software identifier
     * @param capabilities Supported capabilities
     * @param latestBlockNumber Latest known block number
     */
    public Peer(
            byte networkId,
            short networkVersion,
            String peerId,
            String ip,
            int port,
            String clientId,
            String[] capabilities,
            long latestBlockNumber,
            boolean isGenerateBlock,
            String nodeTag
    ) {
        this.networkId = networkId;
        this.ip = ip;
        this.port = port;
        this.peerId = peerId;
        this.networkVersion = networkVersion;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.latestBlockNumber = latestBlockNumber;
        this.isGenerateBlock = isGenerateBlock;
        this.nodeTag = nodeTag;
    }

    /**
     * Encodes peer data using SimpleEncoder for network transmission.
     * 
     * @param enc SimpleEncoder to write data to
     */
    public void encode(SimpleEncoder enc) {
        enc.writeByte(networkId);
        enc.writeShort(networkVersion);
        enc.writeString(peerId != null ? peerId : "");
        enc.writeString(ip != null ? ip : "");
        enc.writeInt(port);
        enc.writeString(clientId != null ? clientId : "");
        
        // Encode capabilities array
        enc.writeInt(capabilities != null ? capabilities.length : 0);
        if (capabilities != null) {
            for (String capability : capabilities) {
                enc.writeString(capability);
            }
        }
        
        enc.writeLong(latestBlockNumber);
        enc.writeBoolean(isGenerateBlock);
        enc.writeString(nodeTag != null ? nodeTag : "");
    }

    /**
     * Decodes peer data from SimpleDecoder.
     * 
     * @param dec SimpleDecoder to read data from
     * @return decoded Peer instance
     */
    public static Peer decode(SimpleDecoder dec) {
        byte networkId = dec.readByte();
        short networkVersion = dec.readShort();
        String peerId = dec.readString();
        String ip = dec.readString();
        int port = dec.readInt();
        String clientId = dec.readString();
        
        // Decode capabilities array
        int capabilitiesCount = dec.readInt();
        String[] capabilities = new String[capabilitiesCount];
        for (int i = 0; i < capabilitiesCount; i++) {
            capabilities[i] = dec.readString();
        }
        
        long latestBlockNumber = dec.readLong();
        boolean isGenerateBlock = dec.readBoolean();
        String nodeTag = dec.readString();
        
        return new Peer(networkId, networkVersion, peerId, ip, port, clientId, 
                       capabilities, latestBlockNumber, isGenerateBlock, nodeTag);
    }

    /**
     * Returns string representation of peer in format: peerId@ip:port
     */
    @Override
    public String toString() {
        return getPeerId() + "@" + ip + ":" + port + ", NodeTag = " + this.nodeTag + ", GenerateBlock = " +
                this.isGenerateBlock;
    }
}