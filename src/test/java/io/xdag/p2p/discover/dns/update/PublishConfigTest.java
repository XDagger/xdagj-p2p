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
package io.xdag.p2p.discover.dns.update;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishConfigTest {

    private PublishConfig config;

    @BeforeEach
    void setUp() {
        config = new PublishConfig();
    }

    @Test
    void testDefaultValues() {
        assertFalse(config.isDnsPublishEnable(), "DNS publish should be disabled by default");
        assertNull(config.getDnsPrivate(), "DNS private key should be null by default");
        assertNotNull(config.getKnownTreeUrls(), "Known tree URLs list should not be null");
        assertTrue(config.getKnownTreeUrls().isEmpty(), "Known tree URLs should be empty by default");
        assertNotNull(config.getStaticNodes(), "Static nodes list should not be null");
        assertTrue(config.getStaticNodes().isEmpty(), "Static nodes should be empty by default");
        assertNull(config.getDnsDomain(), "DNS domain should be null by default");
        assertEquals(0.1, config.getChangeThreshold(), 0.001, "Default change threshold should be 0.1");
        assertEquals(5, config.getMaxMergeSize(), "Default max merge size should be 5");
        assertNull(config.getDnsType(), "DNS type should be null by default");
        assertNull(config.getAccessKeyId(), "Access key ID should be null by default");
        assertNull(config.getAccessKeySecret(), "Access key secret should be null by default");
        assertNull(config.getAwsHostZoneId(), "AWS host zone ID should be null by default");
        assertNull(config.getAwsRegion(), "AWS region should be null by default");
    }

    @Test
    void testSetAndGetDnsPublishEnable() {
        config.setDnsPublishEnable(true);
        assertTrue(config.isDnsPublishEnable(), "DNS publish should be enabled");

        config.setDnsPublishEnable(false);
        assertFalse(config.isDnsPublishEnable(), "DNS publish should be disabled");
    }

    @Test
    void testSetAndGetDnsPrivate() {
        String privateKey = "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291";
        config.setDnsPrivate(privateKey);
        assertEquals(privateKey, config.getDnsPrivate(), "DNS private key should match");
    }

    @Test
    void testSetAndGetKnownTreeUrls() {
        List<String> urls = Arrays.asList(
            "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io",
            "tree://BQHGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@testnet.xdag.io"
        );
        config.setKnownTreeUrls(urls);
        assertEquals(urls, config.getKnownTreeUrls(), "Known tree URLs should match");
        assertEquals(2, config.getKnownTreeUrls().size(), "Should have 2 URLs");
    }

    @Test
    void testSetAndGetStaticNodes() {
        List<InetSocketAddress> nodes = Arrays.asList(
            new InetSocketAddress("192.168.1.100", 16783),
            new InetSocketAddress("192.168.1.101", 16783),
            new InetSocketAddress("192.168.1.102", 16783)
        );
        config.setStaticNodes(nodes);
        assertEquals(nodes, config.getStaticNodes(), "Static nodes should match");
        assertEquals(3, config.getStaticNodes().size(), "Should have 3 static nodes");
    }

    @Test
    void testSetAndGetDnsDomain() {
        String domain = "mainnet.xdag.io";
        config.setDnsDomain(domain);
        assertEquals(domain, config.getDnsDomain(), "DNS domain should match");
    }

    @Test
    void testSetAndGetChangeThreshold() {
        config.setChangeThreshold(0.2);
        assertEquals(0.2, config.getChangeThreshold(), 0.001, "Change threshold should be 0.2");

        config.setChangeThreshold(0.05);
        assertEquals(0.05, config.getChangeThreshold(), 0.001, "Change threshold should be 0.05");

        config.setChangeThreshold(0.5);
        assertEquals(0.5, config.getChangeThreshold(), 0.001, "Change threshold should be 0.5");
    }

    @Test
    void testSetAndGetMaxMergeSize() {
        config.setMaxMergeSize(3);
        assertEquals(3, config.getMaxMergeSize(), "Max merge size should be 3");

        config.setMaxMergeSize(10);
        assertEquals(10, config.getMaxMergeSize(), "Max merge size should be 10");

        config.setMaxMergeSize(1);
        assertEquals(1, config.getMaxMergeSize(), "Max merge size should be 1");
    }

    @Test
    void testSetAndGetDnsType() {
        config.setDnsType(DnsType.AwsRoute53);
        assertEquals(DnsType.AwsRoute53, config.getDnsType(), "DNS type should be AwsRoute53");
    }

    @Test
    void testSetAndGetAccessKeyId() {
        String keyId = "AKIAIOSFODNN7EXAMPLE";
        config.setAccessKeyId(keyId);
        assertEquals(keyId, config.getAccessKeyId(), "Access key ID should match");
    }

    @Test
    void testSetAndGetAccessKeySecret() {
        String keySecret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        config.setAccessKeySecret(keySecret);
        assertEquals(keySecret, config.getAccessKeySecret(), "Access key secret should match");
    }

    @Test
    void testSetAndGetAwsHostZoneId() {
        String zoneId = "Z1234567890ABC";
        config.setAwsHostZoneId(zoneId);
        assertEquals(zoneId, config.getAwsHostZoneId(), "AWS host zone ID should match");
    }

    @Test
    void testSetAndGetAwsRegion() {
        String region = "us-east-1";
        config.setAwsRegion(region);
        assertEquals(region, config.getAwsRegion(), "AWS region should match");
    }

    @Test
    void testCompleteAwsConfiguration() {
        // Complete AWS configuration scenario
        config.setDnsPublishEnable(true);
        config.setDnsPrivate("b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291");
        config.setDnsDomain("mainnet.xdag.io");
        config.setDnsType(DnsType.AwsRoute53);
        config.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        config.setAccessKeySecret("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        config.setAwsHostZoneId("Z1234567890ABC");
        config.setAwsRegion("us-east-1");
        config.setChangeThreshold(0.15);
        config.setMaxMergeSize(4);

        List<String> urls = Arrays.asList("tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io");
        config.setKnownTreeUrls(urls);

        List<InetSocketAddress> nodes = Arrays.asList(
            new InetSocketAddress("10.0.1.100", 16783),
            new InetSocketAddress("10.0.1.101", 16783)
        );
        config.setStaticNodes(nodes);

        // Verify all settings
        assertTrue(config.isDnsPublishEnable());
        assertNotNull(config.getDnsPrivate());
        assertEquals("mainnet.xdag.io", config.getDnsDomain());
        assertEquals(DnsType.AwsRoute53, config.getDnsType());
        assertEquals("AKIAIOSFODNN7EXAMPLE", config.getAccessKeyId());
        assertNotNull(config.getAccessKeySecret());
        assertEquals("Z1234567890ABC", config.getAwsHostZoneId());
        assertEquals("us-east-1", config.getAwsRegion());
        assertEquals(0.15, config.getChangeThreshold(), 0.001);
        assertEquals(4, config.getMaxMergeSize());
        assertEquals(1, config.getKnownTreeUrls().size());
        assertEquals(2, config.getStaticNodes().size());
    }

    @Test
    void testEmptyListsInitialization() {
        // Verify lists are initialized and modifiable
        List<String> urls = new ArrayList<>();
        urls.add("tree://test@example.com");
        config.setKnownTreeUrls(urls);
        assertEquals(1, config.getKnownTreeUrls().size());

        List<InetSocketAddress> nodes = new ArrayList<>();
        nodes.add(new InetSocketAddress("127.0.0.1", 16783));
        config.setStaticNodes(nodes);
        assertEquals(1, config.getStaticNodes().size());
    }

    @Test
    void testBoundaryValues() {
        // Test boundary values for threshold
        config.setChangeThreshold(0.0);
        assertEquals(0.0, config.getChangeThreshold(), 0.001);

        config.setChangeThreshold(1.0);
        assertEquals(1.0, config.getChangeThreshold(), 0.001);

        // Test boundary values for max merge size
        config.setMaxMergeSize(0);
        assertEquals(0, config.getMaxMergeSize());

        config.setMaxMergeSize(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, config.getMaxMergeSize());
    }

    @Test
    void testNullValues() {
        // Set values first
        config.setDnsPrivate("test-key");
        config.setDnsDomain("test.com");
        config.setAccessKeyId("test-id");

        // Set to null
        config.setDnsPrivate(null);
        config.setDnsDomain(null);
        config.setAccessKeyId(null);

        // Verify null
        assertNull(config.getDnsPrivate());
        assertNull(config.getDnsDomain());
        assertNull(config.getAccessKeyId());
    }
}
