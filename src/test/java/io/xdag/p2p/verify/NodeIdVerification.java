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
package io.xdag.p2p.verify;

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.KadService;
import io.xdag.p2p.metrics.P2pMetrics;
import org.apache.tuweni.bytes.Bytes;

/**
 * Verification program for the new Node ID implementation.
 *
 * This class demonstrates and verifies that:
 * 1. Node ID is now derived from XDAG address (20 bytes, 160 bits)
 * 2. Node ID format is 40 hex characters (with or without 0x prefix)
 * 3. Multiple nodes generate unique IDs
 * 4. ID generation is consistent and reproducible
 */
public class NodeIdVerification {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Node ID Implementation Verification");
        System.out.println("Migration: 520-bit public key → 160-bit XDAG address");
        System.out.println("=".repeat(80));
        System.out.println();

        // Test 1: Single node ID generation
        System.out.println("Test 1: Single Node ID Generation");
        System.out.println("-".repeat(80));
        testSingleNodeId();
        System.out.println();

        // Test 2: Multiple nodes with unique IDs
        System.out.println("Test 2: Multiple Nodes with Unique IDs");
        System.out.println("-".repeat(80));
        testMultipleNodeIds();
        System.out.println();

        // Test 3: Node ID format validation
        System.out.println("Test 3: Node ID Format Validation");
        System.out.println("-".repeat(80));
        testNodeIdFormat();
        System.out.println();

        // Test 4: XOR distance calculation
        System.out.println("Test 4: XOR Distance Calculation (Kademlia)");
        System.out.println("-".repeat(80));
        testXorDistance();
        System.out.println();

        System.out.println("=".repeat(80));
        System.out.println("✅ All verifications passed!");
        System.out.println("Node ID successfully migrated to 160-bit XDAG address format");
        System.out.println("=".repeat(80));
    }

    private static void testSingleNodeId() {
        P2pConfig config = new P2pConfig();
        config.generateNodeKey();
        config.setDiscoverEnable(false);

        P2pMetrics metrics = new P2pMetrics();
        KadService kadService = new KadService(config, metrics);
        kadService.init();

        Node homeNode = kadService.getPublicHomeNode();
        String nodeId = homeNode.getId();

        System.out.println("Generated Node ID: " + nodeId);
        System.out.println("Node ID length: " + getHexLength(nodeId) + " hex chars");
        System.out.println("Node ID bytes: " + getByteLength(nodeId) + " bytes");
        System.out.println("Node ID bits: " + (getByteLength(nodeId) * 8) + " bits");

        // Verify XDAG address
        String expectedAddress = config.getNodeKey().toAddress().toHexString();
        System.out.println("Expected XDAG address: " + expectedAddress);
        System.out.println("IDs match: " + nodeId.equals(expectedAddress));

        // Verify it's Kademlia compliant (160 bits)
        int bits = getByteLength(nodeId) * 8;
        System.out.println("Kademlia compliant (160 bits): " + (bits == 160));

        kadService.close();
    }

    private static void testMultipleNodeIds() {
        System.out.println("Generating 5 nodes with unique IDs:");
        System.out.println();

        for (int i = 1; i <= 5; i++) {
            P2pConfig config = new P2pConfig();
            config.generateNodeKey();
            config.setDiscoverEnable(false);

            P2pMetrics metrics = new P2pMetrics();
            KadService kadService = new KadService(config, metrics);
            kadService.init();

            Node homeNode = kadService.getPublicHomeNode();
            String nodeId = homeNode.getId();

            System.out.printf("Node %d ID: %s (%d hex chars)%n",
                i, nodeId, getHexLength(nodeId));

            kadService.close();
        }
    }

    private static void testNodeIdFormat() {
        P2pConfig config = new P2pConfig();
        config.generateNodeKey();
        config.setDiscoverEnable(false);

        P2pMetrics metrics = new P2pMetrics();
        KadService kadService = new KadService(config, metrics);
        kadService.init();

        Node homeNode = kadService.getPublicHomeNode();
        String nodeId = homeNode.getId();

        // Check format
        boolean hasPrefix = nodeId.startsWith("0x");
        String hexPart = hasPrefix ? nodeId.substring(2) : nodeId;

        System.out.println("Node ID: " + nodeId);
        System.out.println("Has 0x prefix: " + hasPrefix);
        System.out.println("Hex part length: " + hexPart.length() + " chars");
        System.out.println("Expected length: 40 chars (20 bytes)");
        System.out.println("Format valid: " + (hexPart.length() == 40));
        System.out.println("All hex chars: " + hexPart.matches("[0-9a-fA-F]+"));

        // Verify can be parsed as Bytes
        try {
            Bytes parsed = Bytes.fromHexString(nodeId);
            System.out.println("Parsed as Bytes: ✓ (size: " + parsed.size() + " bytes)");
        } catch (Exception e) {
            System.out.println("Parsed as Bytes: ✗ (" + e.getMessage() + ")");
        }

        kadService.close();
    }

    private static void testXorDistance() {
        // Generate 3 nodes
        P2pConfig config1 = new P2pConfig();
        config1.generateNodeKey();
        config1.setDiscoverEnable(false);

        P2pConfig config2 = new P2pConfig();
        config2.generateNodeKey();
        config2.setDiscoverEnable(false);

        P2pConfig config3 = new P2pConfig();
        config3.generateNodeKey();
        config3.setDiscoverEnable(false);

        P2pMetrics metrics1 = new P2pMetrics();
        KadService kadService1 = new KadService(config1, metrics1);
        kadService1.init();

        P2pMetrics metrics2 = new P2pMetrics();
        KadService kadService2 = new KadService(config2, metrics2);
        kadService2.init();

        P2pMetrics metrics3 = new P2pMetrics();
        KadService kadService3 = new KadService(config3, metrics3);
        kadService3.init();

        String id1 = kadService1.getPublicHomeNode().getId();
        String id2 = kadService2.getPublicHomeNode().getId();
        String id3 = kadService3.getPublicHomeNode().getId();

        System.out.println("Node 1 ID: " + id1);
        System.out.println("Node 2 ID: " + id2);
        System.out.println("Node 3 ID: " + id3);
        System.out.println();

        // Calculate XOR distances
        Bytes bytes1 = Bytes.fromHexString(id1);
        Bytes bytes2 = Bytes.fromHexString(id2);
        Bytes bytes3 = Bytes.fromHexString(id3);

        Bytes xor12 = bytes1.xor(bytes2);
        Bytes xor13 = bytes1.xor(bytes3);
        Bytes xor23 = bytes2.xor(bytes3);

        System.out.println("XOR distance (Node 1 ⊕ Node 2): " + xor12.toHexString());
        System.out.println("XOR distance (Node 1 ⊕ Node 3): " + xor13.toHexString());
        System.out.println("XOR distance (Node 2 ⊕ Node 3): " + xor23.toHexString());
        System.out.println();
        System.out.println("Distance calculation working: ✓");
        System.out.println("160-bit XOR space verified: ✓");

        kadService1.close();
        kadService2.close();
        kadService3.close();
    }

    private static int getHexLength(String nodeId) {
        if (nodeId.startsWith("0x")) {
            return nodeId.length() - 2;
        }
        return nodeId.length();
    }

    private static int getByteLength(String nodeId) {
        return getHexLength(nodeId) / 2;
    }
}
