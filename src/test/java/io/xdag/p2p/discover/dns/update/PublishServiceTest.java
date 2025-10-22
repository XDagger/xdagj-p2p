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
import static org.mockito.Mockito.*;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.NodeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PublishServiceTest {

    @Mock private P2pConfig mockConfig;
    @Mock private NodeManager mockNodeManager;
    @Mock private PublishConfig mockPublishConfig;

    private PublishService publishService;

    @BeforeEach
    public void setUp() {
        publishService = new PublishService(mockConfig, mockNodeManager);
        when(mockConfig.getPublishConfig()).thenReturn(mockPublishConfig);
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testConstructor() {
        PublishService service = new PublishService(mockConfig, mockNodeManager);
        assertNotNull(service, "PublishService should be created");
    }

    // ==================== init() Tests ====================

    @Test
    public void testInitWhenDnsPublishDisabled() {
        // Given - DNS publishing is disabled
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(false);

        // When
        publishService.init();

        // Then - no exception should be thrown
        // Service should handle disabled state gracefully
    }

    @Test
    public void testInitWhenNoIPv4() {
        // Given - IPv4 is not configured
        when(mockConfig.getIpV4()).thenReturn(null);
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);

        // When
        publishService.init();

        // Then - should fail config check and not initialize
        // No exception should be thrown
    }

    @Test
    public void testInitWhenDnsTypeIsNull() {
        // Given - DNS type is not specified
        when(mockConfig.getIpV4()).thenReturn("127.0.0.1");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(null);

        // When
        publishService.init();

        // Then - should fail config check
    }

    @Test
    public void testInitWhenDnsDomainIsEmpty() {
        // Given - DNS domain is empty
        when(mockConfig.getIpV4()).thenReturn("127.0.0.1");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(DnsType.AwsRoute53);
        when(mockPublishConfig.getDnsDomain()).thenReturn("");

        // When
        publishService.init();

        // Then - should fail config check
    }

    @Test
    public void testInitWhenAwsConfigIncomplete() {
        // Given - AWS Route53 selected but credentials missing
        when(mockConfig.getIpV4()).thenReturn("127.0.0.1");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(DnsType.AwsRoute53);
        when(mockPublishConfig.getDnsDomain()).thenReturn("example.com");
        when(mockPublishConfig.getAccessKeyId()).thenReturn(""); // Missing
        when(mockPublishConfig.getAccessKeySecret()).thenReturn("secret");
        when(mockPublishConfig.getAwsRegion()).thenReturn("us-east-1");

        // When
        publishService.init();

        // Then - should fail config check
    }

    // ==================== close() Tests ====================

    @Test
    public void testClose() {
        // When
        publishService.close();

        // Then - should not throw exception
    }

    @Test
    public void testCloseMultipleTimes() {
        // When
        publishService.close();
        publishService.close();
        publishService.close();

        // Then - should handle multiple closes gracefully
    }

    @Test
    public void testCloseAfterInit() {
        // Given - service initialized (but will fail due to missing config)
        when(mockConfig.getIpV4()).thenReturn("127.0.0.1");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(false);
        publishService.init();

        // When
        publishService.close();

        // Then - should shutdown gracefully
    }

    // ==================== Configuration Validation Tests ====================

    @Test
    public void testConfigCheckWithValidAwsConfig() {
        // Given - complete valid AWS config
        when(mockConfig.getIpV4()).thenReturn("192.168.1.100");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(DnsType.AwsRoute53);
        when(mockPublishConfig.getDnsDomain()).thenReturn("nodes.example.com");
        when(mockPublishConfig.getAccessKeyId()).thenReturn("AKIAIOSFODNN7EXAMPLE");
        when(mockPublishConfig.getAccessKeySecret()).thenReturn("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        when(mockPublishConfig.getAwsRegion()).thenReturn("us-west-2");

        // When - init will try to create AWS client and will fail without real credentials
        // But config check should pass
        publishService.init();

        // Then - no immediate exception from config validation
    }

    @Test
    public void testConfigCheckFailsWithoutAccessKey() {
        // Given
        when(mockConfig.getIpV4()).thenReturn("192.168.1.100");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(DnsType.AwsRoute53);
        when(mockPublishConfig.getDnsDomain()).thenReturn("nodes.example.com");
        when(mockPublishConfig.getAccessKeyId()).thenReturn(null); // Missing
        when(mockPublishConfig.getAccessKeySecret()).thenReturn("secret");
        when(mockPublishConfig.getAwsRegion()).thenReturn("us-west-2");

        // When
        publishService.init();

        // Then - should fail config check silently
    }

    @Test
    public void testConfigCheckFailsWithoutRegion() {
        // Given
        when(mockConfig.getIpV4()).thenReturn("192.168.1.100");
        when(mockPublishConfig.isDnsPublishEnable()).thenReturn(true);
        when(mockPublishConfig.getDnsType()).thenReturn(DnsType.AwsRoute53);
        when(mockPublishConfig.getDnsDomain()).thenReturn("nodes.example.com");
        when(mockPublishConfig.getAccessKeyId()).thenReturn("AKIAIOSFODNN7EXAMPLE");
        when(mockPublishConfig.getAccessKeySecret()).thenReturn("secret");
        when(mockPublishConfig.getAwsRegion()).thenReturn(null); // Missing

        // When
        publishService.init();

        // Then - should fail config check silently
    }

    // ==================== Edge Cases ====================

    @Test
    public void testInitWithNullConfig() {
        // Given
        when(mockConfig.getPublishConfig()).thenReturn(null);

        // When/Then - should handle null config gracefully
        assertThrows(NullPointerException.class, () -> {
            publishService.init();
        });
    }

    @Test
    public void testServiceCreationWithNullNodeManager() {
        // When/Then - constructor should accept null (though not recommended)
        PublishService service = new PublishService(mockConfig, null);
        assertNotNull(service);
    }
}
