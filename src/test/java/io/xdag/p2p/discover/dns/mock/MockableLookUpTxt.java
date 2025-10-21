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

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.lookup.LookUpTxt;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

/**
 * Enhanced DNS lookup utility that supports both real DNS queries and mock DNS resolution.
 *
 * <p>This class extends the standard LookUpTxt functionality by adding support for
 * MockDnsResolver. When mock mode is enabled, DNS queries are resolved from in-memory
 * records instead of real DNS servers.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Enable mock mode
 * MockableLookUpTxt.setMockMode(true);
 *
 * // Add mock records
 * MockDnsResolver.getInstance().addRecord("example.org", "enrtree-root:v1=...");
 *
 * // Query will use mock resolver
 * TXTRecord record = MockableLookUpTxt.lookUpTxt(p2pConfig, "example.org");
 *
 * // Disable mock mode (use real DNS)
 * MockableLookUpTxt.setMockMode(false);
 * }</pre>
 */
@Slf4j(topic = "net")
public class MockableLookUpTxt {

    /** Flag to enable/disable mock DNS resolution */
    private static volatile boolean useMockResolver = false;

    /**
     * Enable or disable mock DNS resolution mode.
     *
     * @param enabled true to use mock resolver, false to use real DNS
     */
    public static void setMockMode(boolean enabled) {
        useMockResolver = enabled;
        log.info("MockableLookUpTxt: Mock mode {}", enabled ? "ENABLED" : "DISABLED");
    }

    /**
     * Check if mock mode is currently enabled.
     *
     * @return true if mock mode is enabled, false otherwise
     */
    public static boolean isMockMode() {
        return useMockResolver;
    }

    /**
     * Lookup TXT record with hash and domain (delegates to lookUpTxt(name)).
     *
     * @param p2pConfig the P2P configuration
     * @param hash the hash prefix
     * @param domain the domain name
     * @return TXTRecord if found, null otherwise
     * @throws TextParseException if DNS text parsing fails
     * @throws UnknownHostException if host is unknown
     */
    public static TXTRecord lookUpTxt(P2pConfig p2pConfig, String hash, String domain)
            throws TextParseException, UnknownHostException {
        return lookUpTxt(p2pConfig, hash + "." + domain);
    }

    /**
     * Lookup TXT record by full domain name.
     *
     * <p>If mock mode is enabled, queries MockDnsResolver. Otherwise, uses real DNS
     * via LookUpTxt.lookUpTxt().
     *
     * @param p2pConfig the P2P configuration (ignored in mock mode)
     * @param name the full domain name to query
     * @return TXTRecord if found, null otherwise
     * @throws TextParseException if DNS text parsing fails
     * @throws UnknownHostException if host is unknown
     */
    public static TXTRecord lookUpTxt(P2pConfig p2pConfig, String name)
            throws TextParseException, UnknownHostException {
        if (useMockResolver) {
            log.debug("MockableLookUpTxt: Using mock resolver for {}", name);
            TXTRecord record = MockDnsResolver.getInstance().lookupTxt(name);
            if (record == null) {
                log.warn("MockableLookUpTxt: No mock record found for {}", name);
            }
            return record;
        } else {
            log.debug("MockableLookUpTxt: Using real DNS for {}", name);
            return LookUpTxt.lookUpTxt(p2pConfig, name);
        }
    }

    /**
     * Join TXT record strings (same as LookUpTxt.joinTXTRecord).
     *
     * @param txtRecord the TXT record to join
     * @return concatenated string
     */
    public static String joinTXTRecord(TXTRecord txtRecord) {
        return LookUpTxt.joinTXTRecord(txtRecord);
    }
}
