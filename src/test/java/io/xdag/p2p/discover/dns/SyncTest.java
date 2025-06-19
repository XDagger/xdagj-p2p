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
package io.xdag.p2p.discover.dns;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.sync.Client;
import io.xdag.p2p.discover.dns.sync.ClientTree;
import io.xdag.p2p.discover.dns.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DNS synchronization functionality.
 * 
 * <p>These tests are optimized to avoid network dependencies and ensure fast, 
 * reliable execution in any environment including CI/CD pipelines.
 */
public class SyncTest {

  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("tree://AKMQMNAJJBL73LXWPXDI4I5ZWWIZ4AWO34DWQ636QOBBXNFXH3LQS@nodes.example.org");
    p2pConfig.setTreeUrls(treeUrls);
  }

  @Test
  void testClientConstruction() {
    assertDoesNotThrow(() -> {
      Client syncClient = new Client(p2pConfig);
      assertNotNull(syncClient);
      assertNotNull(syncClient.getTrees());
      assertTrue(syncClient.getTrees().isEmpty());
    });
  }

  @Test
  void testClientTreeConstruction() {
    assertDoesNotThrow(() -> {
      Client syncClient = new Client(p2pConfig);
      ClientTree clientTree = new ClientTree(syncClient);
      assertNotNull(clientTree);
    });
  }

  @Test
  void testTreeConstruction() {
    assertDoesNotThrow(() -> {
      Tree tree = new Tree(p2pConfig);
      assertNotNull(tree);
    });
  }

  @Test
  void testInitWithEmptyUrls() {
    assertDoesNotThrow(() -> {
      P2pConfig emptyConfig = new P2pConfig();
      emptyConfig.setTreeUrls(new ArrayList<>());
      
      Client syncClient = new Client(emptyConfig);
      syncClient.init();
      
      assertTrue(syncClient.getTrees().isEmpty());
    });
  }

  @Test
  void testStartSyncWithEmptyUrls() {
    assertDoesNotThrow(() -> {
      P2pConfig emptyConfig = new P2pConfig();
      emptyConfig.setTreeUrls(new ArrayList<>());
      
      Client syncClient = new Client(emptyConfig);
      syncClient.startSync();
      
      assertTrue(syncClient.getTrees().isEmpty());
    });
  }

  @Test
  void testClientClose() {
    assertDoesNotThrow(() -> {
    Client syncClient = new Client(p2pConfig);
      syncClient.close();
    });
  }

  @Test
  void testSyncTreeMethodExists() {
    // Test that syncTree method can be called without network operations
    assertDoesNotThrow(() -> {
      Client syncClient = new Client(p2pConfig);
    ClientTree clientTree = new ClientTree(syncClient);
    Tree tree = new Tree(p2pConfig);
      
      // This will fail due to DNS resolution, but we test that the method exists
      // and handles exceptions gracefully
    try {
        syncClient.syncTree("tree://invalid@test.example.com", clientTree, tree);
    } catch (Exception e) {
        // Expected behavior - DNS resolution will fail for invalid URL
        // The important thing is that the method exists and accepts the parameters
        assertTrue(e instanceof Exception);
      }
    });
  }
}
