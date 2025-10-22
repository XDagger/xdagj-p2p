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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.Name;
import org.xbill.DNS.TXTRecord;

/**
 * Mock DNS resolver for local testing of EIP-1459 DNS discovery functionality.
 *
 * <p>This class simulates a DNS server without requiring external DNS infrastructure.
 * It stores DNS TXT records in memory and provides a simple API to query them.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // 1. Initialize the mock resolver
 * MockDnsResolver resolver = MockDnsResolver.getInstance();
 *
 * // 2. Add DNS TXT records
 * resolver.addRecord("example.nodes.example.org",
 *     "enrtree-root:v1=hash1,e=hash2,l=hash3,seq=1,sig=abc123");
 * resolver.addRecord("hash1.example.nodes.example.org",
 *     "enrtree-branch:hash4,hash5,hash6");
 * resolver.addRecord("hash4.example.nodes.example.org",
 *     "enr:-IS4QHC...[base64-encoded-node-record]");
 *
 * // 3. Query records
 * TXTRecord record = resolver.lookupTxt("example.nodes.example.org");
 *
 * // 4. Clear all records when done
 * resolver.clear();
 * }</pre>
 *
 * <p>Thread-safe: This class uses ConcurrentHashMap for thread-safe record storage.
 */
@Slf4j(topic = "net")
public class MockDnsResolver {

    private static final MockDnsResolver INSTANCE = new MockDnsResolver();

    /** In-memory storage for DNS TXT records: domain name -> TXT content */
    private final Map<String, String> txtRecords = new ConcurrentHashMap<>();

    /** Default TTL for TXT records (in seconds) */
    private static final long DEFAULT_TTL = 3600;

    /** DNS class: IN (Internet) */
    private static final int DNS_CLASS_IN = 1;

    private MockDnsResolver() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance of MockDnsResolver.
     *
     * @return the singleton instance
     */
    public static MockDnsResolver getInstance() {
        return INSTANCE;
    }

    /**
     * Add a TXT record to the mock DNS server.
     *
     * @param name the domain name (e.g., "example.nodes.example.org")
     * @param txtContent the TXT record content (e.g., "enrtree-root:v1=...")
     * @return this resolver for method chaining
     */
    public MockDnsResolver addRecord(String name, String txtContent) {
        txtRecords.put(normalizeName(name), txtContent);
        log.debug("MockDnsResolver: Added TXT record for {}: {}", name, txtContent);
        return this;
    }

    /**
     * Add multiple TXT record strings for a single domain (simulates DNS TXT record splitting).
     *
     * @param name the domain name
     * @param txtContents list of TXT record strings
     * @return this resolver for method chaining
     */
    public MockDnsResolver addRecord(String name, List<String> txtContents) {
        String joined = String.join("", txtContents);
        return addRecord(name, joined);
    }

    /**
     * Lookup a TXT record by domain name.
     *
     * @param name the domain name to query
     * @return TXTRecord object if found, null otherwise
     */
    public TXTRecord lookupTxt(String name) {
        String normalizedName = normalizeName(name);
        String txtContent = txtRecords.get(normalizedName);

        if (txtContent == null) {
            log.warn("MockDnsResolver: No TXT record found for {}", name);
            return null;
        }

        try {
            Name dnsName = Name.fromString(normalizedName + ".");
            TXTRecord record = new TXTRecord(dnsName, DNS_CLASS_IN, DEFAULT_TTL, txtContent);
            log.debug("MockDnsResolver: Resolved {} -> {}", name, txtContent);
            return record;
        } catch (Exception e) {
            log.error("MockDnsResolver: Failed to create TXTRecord for {}", name, e);
            return null;
        }
    }

    /**
     * Check if a TXT record exists for the given domain name.
     *
     * @param name the domain name to check
     * @return true if record exists, false otherwise
     */
    public boolean hasRecord(String name) {
        return txtRecords.containsKey(normalizeName(name));
    }

    /**
     * Remove a TXT record from the mock DNS server.
     *
     * @param name the domain name to remove
     * @return the removed TXT content, or null if not found
     */
    public String removeRecord(String name) {
        String removed = txtRecords.remove(normalizeName(name));
        if (removed != null) {
            log.debug("MockDnsResolver: Removed TXT record for {}", name);
        }
        return removed;
    }

    /**
     * Clear all TXT records from the mock DNS server.
     */
    public void clear() {
        int count = txtRecords.size();
        txtRecords.clear();
        log.info("MockDnsResolver: Cleared {} TXT records", count);
    }

    /**
     * Get the total number of records in the mock DNS server.
     *
     * @return the number of stored records
     */
    public int size() {
        return txtRecords.size();
    }

    /**
     * Normalize domain name by removing trailing dots and converting to lowercase.
     *
     * @param name the domain name to normalize
     * @return normalized domain name
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("\\.$", "");
    }

    /**
     * Print all stored TXT records (for debugging).
     */
    public void printAllRecords() {
        log.info("MockDnsResolver: Total {} records", txtRecords.size());
        txtRecords.forEach((name, content) -> {
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("  {} -> {}", name, preview);
        });
    }
}
