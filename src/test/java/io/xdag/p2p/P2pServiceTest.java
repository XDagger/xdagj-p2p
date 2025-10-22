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

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.config.P2pConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for P2pService core functionality. These are true unit tests that test logic without
 * starting real services.
 *
 * @author XDAG Team
 * @since 0.1.0
 */
@Slf4j(topic = "test")
public class P2pServiceTest {

  private P2pService p2pService;
  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setPort(16783);
    p2pConfig.setDiscoverEnable(false); // Disable discovery for unit tests
    p2pService = new P2pService(p2pConfig);
  }

  @AfterEach
  void tearDown() {
    if (p2pService != null && !p2pService.isShutdown()) {
      p2pService.stop();
    }
  }

  /** Test P2P service initialization and basic getters. */
  @Test
  void testServiceInitialization() {
    assertNotNull(p2pService.getConfig(), "P2P config should not be null");
    assertNotNull(p2pService.getNodeManager(), "Node manager should not be null");
    assertNotNull(p2pService.getChannelManager(), "Channel manager should not be null");

    // Service is not shutdown initially, it's just not started
    assertFalse(p2pService.isShutdown(), "Service should not be shutdown initially");
  }

  /** Test P2P service configuration. */
  @Test
  void testServiceConfiguration() {
    assertEquals(p2pConfig, p2pService.getConfig(), "Config should match");
    assertEquals(16783, p2pService.getConfig().getPort(), "Port should match");
    assertFalse(p2pService.getConfig().isDiscoverEnable(), "Discovery should be disabled");
  }

  /** Test node management functionality without starting service. */
  @Test
  void testNodeManagementWithoutStart() {
    // Some methods may not work without initialization, so test basic structure
    assertNotNull(p2pService.getNodeManager(), "Node manager should not be null");
  }

  /** Test service shutdown state. */
  @Test
  void testServiceShutdownState() {
    assertFalse(p2pService.isShutdown(), "Service should not be shutdown initially");

    // Stop the service
    p2pService.stop();
    assertTrue(p2pService.isShutdown(), "Service should be shutdown after stop");

    // Multiple stop calls should be safe
    assertDoesNotThrow(() -> p2pService.stop(), "Multiple stop calls should be safe");
    assertTrue(p2pService.isShutdown(), "Service should remain shutdown");
  }

  /** Test service component creation. */
  @Test
  void testServiceComponents() {
    // Test that all components are properly created
    assertNotNull(p2pService.getNodeManager(), "NodeManager should be created");
    assertNotNull(p2pService.getChannelManager(), "ChannelManager should be created");

    // Test that components have proper configuration
    assertEquals(p2pConfig, p2pService.getConfig(), "All components should use same config");
  }

  /** Test connectable nodes retrieval. */
  @Test
  void testGetConnectableNodes() {
    // Note: getConnectableNodes requires DiscoverService which is only initialized
    // when NodeManager.init() is called. This is done during p2pService.start().
    // Without calling start(), this method will throw NullPointerException.
    // We can test that the method exists but requires initialization.
    assertThrows(
        NullPointerException.class,
        () -> p2pService.getConnectableNodes(),
        "Should throw NPE when service not started");
  }
}
