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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Peer class, focusing on encode/decode functionality
 */
class PeerTest {

    @Test
    void testPeerEncodeDecodeRoundTrip() {
        // Create a peer with all fields populated
        byte networkId = 1;
        short networkVersion = 100;
        String peerId = "test-peer-id-12345";
        String ip = "192.168.1.100";
        int port = 30303;
        String clientId = "xdagj-p2p/0.1.1";
        String[] capabilities = {"DISC_V5", "ETH_66", "SNAP_1"};
        long latestBlockNumber = 123456789L;
        boolean isGenerateBlock = true;
        String nodeTag = "test-node-tag";

        Peer originalPeer = new Peer(
                networkId, networkVersion, peerId, ip, port,
                clientId, capabilities, latestBlockNumber,
                isGenerateBlock, nodeTag
        );

        // Encode
        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        assertNotNull(encoded);
        assertTrue(encoded.length > 0);

        // Decode
        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        // Verify all fields
        assertNotNull(decodedPeer);
        assertEquals(originalPeer.getNetworkId(), decodedPeer.getNetworkId());
        assertEquals(originalPeer.getNetworkVersion(), decodedPeer.getNetworkVersion());
        assertEquals(originalPeer.getPeerId(), decodedPeer.getPeerId());
        assertEquals(originalPeer.getIp(), decodedPeer.getIp());
        assertEquals(originalPeer.getPort(), decodedPeer.getPort());
        assertEquals(originalPeer.getClientId(), decodedPeer.getClientId());
        assertArrayEquals(originalPeer.getCapabilities(), decodedPeer.getCapabilities());
        assertEquals(originalPeer.getLatestBlockNumber(), decodedPeer.getLatestBlockNumber());
        assertEquals(originalPeer.isGenerateBlock(), decodedPeer.isGenerateBlock());
        assertEquals(originalPeer.getNodeTag(), decodedPeer.getNodeTag());
    }

    @Test
    void testPeerEncodeDecodeWithEmptyCapabilities() {
        // Test with empty capabilities array
        Peer originalPeer = new Peer(
                (byte) 1, (short) 1, "peer123", "10.0.0.1", 8080,
                "client-v1", new String[0], 0L, false, "node1"
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        assertNotNull(decodedPeer.getCapabilities());
        assertEquals(0, decodedPeer.getCapabilities().length);
    }

    @Test
    void testPeerEncodeDecodeWithNullFields() {
        // Test with null optional fields
        Peer originalPeer = new Peer(
                (byte) 2, (short) 2, null, null, 9999,
                null, null, 999L, true, null
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        // Null strings are encoded as empty strings
        assertEquals("", decodedPeer.getPeerId());
        assertEquals("", decodedPeer.getIp());
        assertEquals("", decodedPeer.getClientId());
        assertEquals("", decodedPeer.getNodeTag());
        assertEquals(0, decodedPeer.getCapabilities().length);
    }

    @Test
    void testPeerEncodeDecodeMultipleCapabilities() {
        // Test with multiple capabilities
        String[] capabilities = {"CAP1", "CAP2", "CAP3", "CAP4", "CAP5"};
        Peer originalPeer = new Peer(
                (byte) 3, (short) 3, "peer-multi", "172.16.0.1", 5000,
                "multi-client", capabilities, 777L, false, "multi-node"
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        assertArrayEquals(capabilities, decodedPeer.getCapabilities());
    }

    @Test
    void testPeerEncodeDecodeWithSpecialCharacters() {
        // Test with special characters in string fields
        String[] capabilities = {"测试", "тест", "🚀"};
        Peer originalPeer = new Peer(
                (byte) 4, (short) 4, "peer-特殊字符", "fe80::1", 6000,
                "client-中文版", capabilities, 888L, true, "node-🌟"
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        assertEquals(originalPeer.getPeerId(), decodedPeer.getPeerId());
        assertEquals(originalPeer.getClientId(), decodedPeer.getClientId());
        assertEquals(originalPeer.getNodeTag(), decodedPeer.getNodeTag());
        assertArrayEquals(originalPeer.getCapabilities(), decodedPeer.getCapabilities());
    }

    @Test
    void testPeerEncodeDecodeWithBoundaryValues() {
        // Test with boundary values
        Peer originalPeer = new Peer(
                Byte.MAX_VALUE,
                Short.MAX_VALUE,
                "peer-max",
                "255.255.255.255",
                65535, // max port
                "client-max",
                new String[]{"MAX_CAP"},
                Long.MAX_VALUE,
                true,
                "node-max"
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        assertEquals(Byte.MAX_VALUE, decodedPeer.getNetworkId());
        assertEquals(Short.MAX_VALUE, decodedPeer.getNetworkVersion());
        assertEquals(65535, decodedPeer.getPort());
        assertEquals(Long.MAX_VALUE, decodedPeer.getLatestBlockNumber());
    }

    @Test
    void testPeerEncodeDecodeMinimalData() {
        // Test with minimal valid data
        Peer originalPeer = new Peer(
                (byte) 0, (short) 0, "", "", 0,
                "", new String[0], 0L, false, ""
        );

        SimpleEncoder encoder = new SimpleEncoder();
        originalPeer.encode(encoder);
        byte[] encoded = encoder.toBytes();

        SimpleDecoder decoder = new SimpleDecoder(encoded);
        Peer decodedPeer = Peer.decode(decoder);

        assertEquals(0, decodedPeer.getNetworkId());
        assertEquals(0, decodedPeer.getNetworkVersion());
        assertEquals("", decodedPeer.getPeerId());
        assertEquals(0, decodedPeer.getPort());
        assertFalse(decodedPeer.isGenerateBlock());
    }

    @Test
    void testPeerToString() {
        // Test toString method
        Peer peer = new Peer(
                (byte) 1, (short) 1, "test-peer", "127.0.0.1", 8080,
                "test-client", new String[]{"TEST"}, 100L, true, "test-tag"
        );

        String str = peer.toString();
        assertNotNull(str);
        assertTrue(str.contains("test-peer"));
        assertTrue(str.contains("127.0.0.1"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("test-tag"));
        assertTrue(str.contains("true"));
    }

    @Test
    void testPeerLatencySetterGetter() {
        // Test latency setter and getter
        Peer peer = new Peer(
                (byte) 1, (short) 1, "peer", "192.168.0.1", 3000,
                "client", new String[0], 0L, false, "node"
        );

        assertEquals(0, peer.getLatency()); // default value

        peer.setLatency(150L);
        assertEquals(150L, peer.getLatency());
    }

    @Test
    void testPeerLatestBlockNumberSetterGetter() {
        // Test latestBlockNumber setter and getter
        Peer peer = new Peer(
                (byte) 1, (short) 1, "peer", "192.168.0.1", 3000,
                "client", new String[0], 500L, false, "node"
        );

        assertEquals(500L, peer.getLatestBlockNumber());

        peer.setLatestBlockNumber(1000L);
        assertEquals(1000L, peer.getLatestBlockNumber());
    }
}
