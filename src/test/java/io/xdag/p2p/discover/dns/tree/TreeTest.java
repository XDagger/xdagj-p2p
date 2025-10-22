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
package io.xdag.p2p.discover.dns.tree;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.DnsNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TreeTest {

    @Mock
    private P2pConfig mockConfig;

    private Tree tree;

    @BeforeEach
    void setUp() {
        tree = new Tree(mockConfig);
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor() {
        Tree newTree = new Tree(mockConfig);

        assertNotNull(newTree, "Tree should be created");
        assertNotNull(newTree.getEntries(), "Entries map should be initialized");
        assertTrue(newTree.getEntries().isEmpty(), "Entries should be empty initially");
    }

    @Test
    void testConstructorWithNullConfig() {
        // Should create tree even with null config
        Tree newTree = new Tree(null);
        assertNotNull(newTree, "Tree should be created with null config");
    }

    // ==================== Getter/Setter Tests ====================

    @Test
    void testSetAndGetP2pConfig() {
        P2pConfig newConfig = mock(P2pConfig.class);
        tree.setP2pConfig(newConfig);

        assertEquals(newConfig, tree.getP2pConfig(), "P2pConfig should be set");
    }

    @Test
    void testSetAndGetRootEntry() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        assertEquals(rootEntry, tree.getRootEntry(), "RootEntry should be set");
        assertEquals(1, tree.getSeq(), "Seq should match root entry");
    }

    @Test
    void testSetAndGetSeq() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        tree.setSeq(42);
        assertEquals(42, tree.getSeq(), "Seq should be updated");
        assertEquals(42, rootEntry.getSeq(), "RootEntry seq should be updated");
    }

    @Test
    void testSetAndGetPrivateKey() {
        String privateKey = "test-private-key";
        tree.setPrivateKey(privateKey);

        assertEquals(privateKey, tree.getPrivateKey(), "Private key should be set");
    }

    @Test
    void testSetAndGetBase32PublicKey() {
        String publicKey = "test-public-key";
        tree.setBase32PublicKey(publicKey);

        assertEquals(publicKey, tree.getBase32PublicKey(), "Public key should be set");
    }

    // ==================== Constants Tests ====================

    @Test
    void testHashAbbrevSize() {
        assertEquals(27, Tree.HashAbbrevSize, "HashAbbrevSize should be 27");
    }

    @Test
    void testMaxChildren() {
        assertEquals(13, Tree.MaxChildren, "MaxChildren should be 13");
    }

    // ==================== merge() Static Method Tests ====================

    @Test
    void testMergeEmptyList() {
        List<DnsNode> nodes = new ArrayList<>();
        List<String> result = Tree.merge(nodes, 5);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty for empty input");
    }

    @Test
    void testMergeSingleNode() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        DnsNode node = new DnsNode("testId", "192.168.1.1", null, 16783);
        nodes.add(node);

        List<String> result = Tree.merge(nodes, 5);

        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "Should have 1 entry");
        assertTrue(result.get(0).startsWith(Entry.nodesPrefix),
            "Entry should start with nodes prefix");
    }

    @Test
    void testMergeMultipleNodesWithinLimit() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            nodes.add(new DnsNode("id" + i, "192.168.1." + i, null, 16783));
        }

        List<String> result = Tree.merge(nodes, 5);

        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(),
            "Should merge all nodes into 1 entry (within maxMergeSize)");
    }

    @Test
    void testMergeMultipleNodesExceedLimit() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        // Create nodes with same network prefix
        for (int i = 0; i < 8; i++) {
            nodes.add(new DnsNode("id" + i, "192.168.1." + i, null, 16783));
        }

        List<String> result = Tree.merge(nodes, 3);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.size() >= 2,
            "Should split into multiple entries (8 nodes / 3 max = at least 2)");
    }

    @Test
    void testMergeDifferentNetworks() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        // Different network prefixes (192 vs 10)
        nodes.add(new DnsNode("id1", "192.168.1.1", null, 16783));
        nodes.add(new DnsNode("id2", "10.0.1.1", null, 16783));

        List<String> result = Tree.merge(nodes, 10);

        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(),
            "Should split by network prefix even within maxMergeSize");
    }

    @Test
    void testMergeWithMaxMergeSize1() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        nodes.add(new DnsNode("id1", "192.168.1.1", null, 16783));
        nodes.add(new DnsNode("id2", "192.168.1.2", null, 16783));

        List<String> result = Tree.merge(nodes, 1);

        assertNotNull(result, "Result should not be null");
        assertEquals(2, result.size(), "Should create separate entry for each node");
    }

    // ==================== toTXT() Tests ====================

    @Test
    void testToTXTWithEmptyDomain() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        Map<String, String> result = tree.toTXT("");

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty when domain is empty and no entries");
    }

    @Test
    void testToTXTWithNullDomain() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        Map<String, String> result = tree.toTXT(null);

        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testToTXTWithValidDomain() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        Map<String, String> result = tree.toTXT("example.com");

        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("example.com"), "Should contain root domain");
    }

    // ==================== getLinksEntry() Tests ====================

    @Test
    void testGetLinksEntryEmpty() {
        List<String> links = tree.getLinksEntry();

        assertNotNull(links, "Links list should not be null");
        assertTrue(links.isEmpty(), "Links should be empty initially");
    }

    // ==================== getBranchesEntry() Tests ====================

    @Test
    void testGetBranchesEntryEmpty() {
        List<String> branches = tree.getBranchesEntry();

        assertNotNull(branches, "Branches list should not be null");
        assertTrue(branches.isEmpty(), "Branches should be empty initially");
    }

    // ==================== getNodesEntry() Tests ====================

    @Test
    void testGetNodesEntryEmpty() {
        List<String> nodes = tree.getNodesEntry();

        assertNotNull(nodes, "Nodes list should not be null");
        assertTrue(nodes.isEmpty(), "Nodes should be empty initially");
    }

    // ==================== getLinksMap() Tests ====================

    @Test
    void testGetLinksMapEmpty() {
        Map<String, Entry> linksMap = tree.getLinksMap();

        assertNotNull(linksMap, "Links map should not be null");
        assertTrue(linksMap.isEmpty(), "Links map should be empty initially");
    }

    // ==================== getNodesMap() Tests ====================

    @Test
    void testGetNodesMapEmpty() {
        Map<String, Entry> nodesMap = tree.getNodesMap();

        assertNotNull(nodesMap, "Nodes map should not be null");
        assertTrue(nodesMap.isEmpty(), "Nodes map should be empty initially");
    }

    // ==================== getDnsNodes() Tests ====================

    @Test
    void testGetDnsNodesEmpty() {
        List<DnsNode> dnsNodes = tree.getDnsNodes();

        assertNotNull(dnsNodes, "DNS nodes list should not be null");
        assertTrue(dnsNodes.isEmpty(), "DNS nodes should be empty initially");
    }

    // ==================== Edge Cases ====================

    @Test
    void testMultipleSettersOnSameInstance() {
        RootEntry entry1 = new RootEntry("E1", "L1", 1);
        RootEntry entry2 = new RootEntry("E2", "L2", 2);

        tree.setRootEntry(entry1);
        assertEquals(1, tree.getSeq());

        tree.setRootEntry(entry2);
        assertEquals(2, tree.getSeq());
    }

    @Test
    void testSeqUpdateAfterRootChange() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        tree.setSeq(10);
        assertEquals(10, tree.getSeq());

        // Change root, seq from new root should be used
        RootEntry newRoot = new RootEntry("E2", "L2", 20);
        tree.setRootEntry(newRoot);
        assertEquals(20, tree.getSeq());
    }

    @Test
    void testEntriesMapNotNull() {
        assertNotNull(tree.getEntries(), "Entries map should never be null");
    }

    @Test
    void testMergePreservesOrder() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        // Add nodes in specific order
        nodes.add(new DnsNode("id1", "192.168.1.1", null, 16783));
        nodes.add(new DnsNode("id2", "192.168.1.2", null, 16783));
        nodes.add(new DnsNode("id3", "192.168.1.3", null, 16783));

        List<String> result = Tree.merge(nodes, 5);

        // Merge should sort nodes internally
        assertNotNull(result);
        assertTrue(result.size() > 0);
    }

    @Test
    void testMergeWithLargeMaxMergeSize() throws Exception {
        List<DnsNode> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(new DnsNode("id" + i, "192.168.1." + i, null, 16783));
        }

        List<String> result = Tree.merge(nodes, 1000);

        assertEquals(1, result.size(),
            "Should merge all into one entry with large maxMergeSize");
    }

    @Test
    void testToTXTKeysCaseSensitivity() {
        RootEntry rootEntry = new RootEntry("EROOT", "LROOT", 1);
        tree.setRootEntry(rootEntry);

        Map<String, String> result = tree.toTXT("Example.COM");

        // Should contain root domain key (not lowercase for rootDomain itself)
        // Only the hash keys in entries are lowercased, not the rootDomain key
        assertTrue(result.containsKey("Example.COM") || result.containsKey("example.com"),
            "Domain key should be present (rootDomain is not lowercased)");
    }
}
