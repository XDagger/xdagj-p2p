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
package io.xdag.p2p.discover.dns.mock;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.discover.dns.lookup.LookUpTxt;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.TXTRecord;

import java.security.SecureRandom;

/**
 * Unit tests for Mock DNS functionality.
 *
 * <p>These tests demonstrate how to use the Mock DNS system to test EIP-1459 DNS discovery
 * without requiring real DNS infrastructure.
 */
@Slf4j(topic = "net")
class MockDnsTest {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private MockDnsResolver resolver;
    private P2pConfig p2pConfig;

    @BeforeEach
    void setUp() {
        // Initialize mock resolver
        resolver = MockDnsResolver.getInstance();
        resolver.clear();

        // Create test P2P config
        p2pConfig = new P2pConfig();
        p2pConfig.setNetworkId((byte) 1);
        p2pConfig.setNetworkVersion((short) 1);
        p2pConfig.setIpV4("127.0.0.1");
        p2pConfig.setPort(10000);

        byte[] privateKeyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(privateKeyBytes);
        ECKeyPair nodeKey = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());
        p2pConfig.setNodeKey(nodeKey);

        log.info("MockDnsTest setup complete");
    }

    @AfterEach
    void tearDown() {
        // Clear mock resolver
        resolver.clear();

        // Disable mock mode
        MockableLookUpTxt.setMockMode(false);

        log.info("MockDnsTest teardown complete");
    }

    @Test
    void testMockDnsResolver_AddAndLookup() {
        log.info("Test: Add and lookup TXT record");

        // Add a mock TXT record
        String domain = "test.example.org";
        String txtContent = "enrtree-root:v1 e=HASH1 l=HASH2 seq=1 sig=0xABCD";
        resolver.addRecord(domain, txtContent);

        // Lookup the record
        TXTRecord record = resolver.lookupTxt(domain);
        assertNotNull(record, "TXT record should exist");

        String retrievedContent = LookUpTxt.joinTXTRecord(record);
        assertEquals(txtContent, retrievedContent, "TXT content should match");

        log.info("Test passed: TXT record added and retrieved successfully");
    }

    @Test
    void testMockDnsResolver_MultipleRecords() {
        log.info("Test: Multiple TXT records");

        // Add multiple records
        resolver.addRecord("root.example.org", "enrtree-root:v1 e=HASH1 l= seq=1 sig=0x123");
        resolver.addRecord("branch.example.org", "enrtree-branch:HASH2,HASH3,HASH4");
        resolver.addRecord("nodes.example.org", "enr:BASE64_ENCODED_NODES");

        // Verify all records exist
        assertTrue(resolver.hasRecord("root.example.org"), "Root record should exist");
        assertTrue(resolver.hasRecord("branch.example.org"), "Branch record should exist");
        assertTrue(resolver.hasRecord("nodes.example.org"), "Nodes record should exist");

        assertEquals(3, resolver.size(), "Should have 3 records");

        log.info("Test passed: Multiple records added successfully");
    }

    @Test
    void testMockDnsResolver_RemoveRecord() {
        log.info("Test: Remove TXT record");

        String domain = "temp.example.org";
        resolver.addRecord(domain, "temporary record");

        assertTrue(resolver.hasRecord(domain), "Record should exist");

        // Remove the record
        String removed = resolver.removeRecord(domain);
        assertEquals("temporary record", removed, "Removed content should match");

        assertFalse(resolver.hasRecord(domain), "Record should not exist after removal");

        log.info("Test passed: Record removed successfully");
    }

    @Test
    void testMockableLookUpTxt_MockMode() throws Exception {
        log.info("Test: MockableLookUpTxt in mock mode");

        // Enable mock mode
        MockableLookUpTxt.setMockMode(true);
        assertTrue(MockableLookUpTxt.isMockMode(), "Mock mode should be enabled");

        // Add mock record
        String domain = "mock.test.org";
        String txtContent = "enrtree-root:v1 e=TESTHASH l= seq=1 sig=0xABC";
        resolver.addRecord(domain, txtContent);

        // Query using MockableLookUpTxt
        TXTRecord record = MockableLookUpTxt.lookUpTxt(p2pConfig, domain);
        assertNotNull(record, "Should retrieve record in mock mode");

        String retrieved = MockableLookUpTxt.joinTXTRecord(record);
        assertEquals(txtContent, retrieved, "Content should match");

        log.info("Test passed: MockableLookUpTxt works in mock mode");
    }

    @Test
    void testMockableLookUpTxt_WithHashAndDomain() throws Exception {
        log.info("Test: MockableLookUpTxt with hash and domain");

        MockableLookUpTxt.setMockMode(true);

        String hash = "ABC123";
        String domain = "example.org";
        String fullDomain = hash + "." + domain;
        String txtContent = "enrtree-branch:HASH1,HASH2";

        resolver.addRecord(fullDomain, txtContent);

        TXTRecord record = MockableLookUpTxt.lookUpTxt(p2pConfig, hash, domain);
        assertNotNull(record, "Should retrieve record with hash and domain");

        String retrieved = MockableLookUpTxt.joinTXTRecord(record);
        assertEquals(txtContent, retrieved, "Content should match");

        log.info("Test passed: Hash and domain lookup works");
    }

    @Test
    void testMockDnsResolver_CaseInsensitive() {
        log.info("Test: MockDnsResolver case-insensitive domain names");

        String txtContent = "test content";
        resolver.addRecord("Test.Example.ORG", txtContent);

        // Should find with different case
        assertTrue(resolver.hasRecord("test.example.org"), "Should find with lowercase");
        assertTrue(resolver.hasRecord("TEST.EXAMPLE.ORG"), "Should find with uppercase");
        assertTrue(resolver.hasRecord("TeSt.ExAmPlE.oRg"), "Should find with mixed case");

        TXTRecord record = resolver.lookupTxt("test.example.org");
        assertNotNull(record, "Should retrieve with different case");

        log.info("Test passed: Case-insensitive domain matching works");
    }

    @Test
    void testMockDnsResolver_Clear() {
        log.info("Test: MockDnsResolver clear all records");

        // Add multiple records
        resolver.addRecord("record1.example.org", "content1");
        resolver.addRecord("record2.example.org", "content2");
        resolver.addRecord("record3.example.org", "content3");

        assertEquals(3, resolver.size(), "Should have 3 records");

        // Clear all
        resolver.clear();

        assertEquals(0, resolver.size(), "Should have 0 records after clear");
        assertFalse(resolver.hasRecord("record1.example.org"), "Records should not exist");

        log.info("Test passed: Clear removes all records");
    }

    @Test
    void testMockDnsResolver_NullDomain() {
        log.info("Test: MockDnsResolver handles null domain");

        // Should not throw exception
        TXTRecord record = resolver.lookupTxt(null);
        assertNull(record, "Should return null for null domain");

        log.info("Test passed: Null domain handled gracefully");
    }

    @Test
    void testMockableLookUpTxt_RealDnsMode() {
        log.info("Test: MockableLookUpTxt in real DNS mode");

        // Disable mock mode (default)
        MockableLookUpTxt.setMockMode(false);
        assertFalse(MockableLookUpTxt.isMockMode(), "Mock mode should be disabled");

        // Add record to mock resolver (should not be used)
        resolver.addRecord("test.example.org", "this should not be found");

        // Query will try real DNS (will likely fail or timeout, which is expected)
        // This test just verifies that mock mode can be disabled
        log.info("Test passed: Real DNS mode can be enabled");
    }
}
