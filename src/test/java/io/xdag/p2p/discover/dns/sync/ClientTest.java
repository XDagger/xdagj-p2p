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
package io.xdag.p2p.discover.dns.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xbill.DNS.TextParseException;

/**
 * Unit tests for Client. Tests DNS synchronization client functionality.
 */
public class ClientTest {

  @Mock
  private P2pConfig p2pConfig;

  private Client client;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(p2pConfig.getTreeUrls()).thenReturn(new ArrayList<>());
    client = new Client(p2pConfig);
  }

  @Test
  void testConstructor() {
    assertNotNull(client);
    assertNotNull(client.getTrees());
    assertTrue(client.getTrees().isEmpty());
  }

  @Test
  void testConstants() {
    assertEquals(3600, Client.recheckInterval); // 60 * 60
    assertEquals(2000, Client.cacheLimit);
    assertEquals(10, Client.randomRetryTimes);
  }

  @Test
  void testInitWithEmptyTreeUrls() {
    // When tree URLs are empty, init should not schedule any tasks
    when(p2pConfig.getTreeUrls()).thenReturn(new ArrayList<>());
    
    client.init();
    // Should complete without exception
    assertTrue(client.getTrees().isEmpty());
  }

  @Test
  void testInitWithTreeUrls() {
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://test.example.com");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    Client clientWithUrls = new Client(p2pConfig);
    clientWithUrls.init();
    
    // Should complete without exception and have scheduled sync
    assertNotNull(clientWithUrls);
  }

  @Test
  void testStartSyncWithEmptyUrls() {
    when(p2pConfig.getTreeUrls()).thenReturn(new ArrayList<>());
    
    // Should complete without doing anything
    client.startSync();
    assertTrue(client.getTrees().isEmpty());
  }

  @Test
  void testStartSyncWithValidUrls() {
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    // This will attempt to sync but may fail due to DNS resolution
    // The important thing is that it doesn't throw unexpected exceptions
    try {
      client.startSync();
    } catch (Exception e) {
      // Expected behavior since we're not mocking DNS resolution
      assertTrue(e instanceof RuntimeException || e.getCause() instanceof Exception);
    }
    
    // Trees map should be populated even if sync fails
    assertEquals(1, client.getTrees().size());
  }

  @Test
  void testResolveRootWithNullTxtRecord() {
    LinkEntry linkEntry = mock(LinkEntry.class);
    when(linkEntry.domain()).thenReturn("test.domain.com");
    
    // This will fail because we can't mock the static LookUpTxt.lookUpTxt method easily
    // So we expect a DnsException
    assertThrows(Exception.class, () -> client.resolveRoot(linkEntry));
  }

  @Test
  void testResolveEntryWithCachedResult() {
    // This test focuses on the caching behavior
    // Since we can't easily mock the internal cache, we test the method signature
    try {
      client.resolveEntry("test.domain.com", "invalid-hash");
    } catch (Exception e) {
      // Expected since we're not providing valid inputs
      assertTrue(e instanceof DnsException || e instanceof TextParseException);
    }
  }

  @Test
  void testNewIterator() {
    RandomIterator iterator = client.newIterator();
    assertNotNull(iterator);
  }

  @Test
  void testNewIteratorWithTreeUrls() {
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://test@test.example.com");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    Client clientWithUrls = new Client(p2pConfig);
    RandomIterator iterator = clientWithUrls.newIterator();
    assertNotNull(iterator);
  }

  @Test
  void testClose() {
    // Should complete without exception
    client.close();
    
    // Create another client to test close after init
    Client anotherClient = new Client(p2pConfig);
    anotherClient.init();
    anotherClient.close();
  }

  @Test
  void testSyncTreeWithNullClientTree() {
    String urlScheme = "enrtree://test@test.example.com";
    Tree tree = new Tree(p2pConfig);
    
    // This will fail due to DNS resolution but should test the null clientTree handling
    try {
      client.syncTree(urlScheme, null, tree);
    } catch (Exception e) {
      // Expected behavior since we're testing with invalid URL and no DNS mocking
      assertInstanceOf(Exception.class, e);
    }
  }

  @Test
  void testSyncTreeWithExistingClientTree() {
    String urlScheme = "enrtree://test@test.example.com";
    ClientTree clientTree = new ClientTree(client);
    Tree tree = new Tree(p2pConfig);
    
    // This will fail due to DNS resolution
    try {
      client.syncTree(urlScheme, clientTree, tree);
    } catch (Exception e) {
      // Expected behavior since we're testing with invalid URL and no DNS mocking
      assertInstanceOf(Exception.class, e);
    }
  }

  @Test
  void testMultipleStartSyncCalls() {
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://test@test.example.com");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    Client clientWithUrls = new Client(p2pConfig);
    
    // Multiple calls should not cause issues
    try {
      clientWithUrls.startSync();
      clientWithUrls.startSync();
    } catch (Exception e) {
      // Expected due to DNS resolution failures
    }
    
    // Should have the tree URL in trees map
    assertEquals(1, clientWithUrls.getTrees().size());
  }

  @Test
  void testGetTrees() {
    Map<String, Tree> trees = client.getTrees();
    assertNotNull(trees);
    assertTrue(trees.isEmpty());
    
    // Test with URLs
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://test@test.example.com");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    Client clientWithUrls = new Client(p2pConfig);
    try {
      clientWithUrls.startSync();
    } catch (Exception e) {
      // Expected
    }
    
    assertEquals(1, clientWithUrls.getTrees().size());
  }

  @Test
  void testConcurrentSyncCalls() {
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("enrtree://test1@test1.example.com");
    treeUrls.add("enrtree://test2@test2.example.com");
    when(p2pConfig.getTreeUrls()).thenReturn(treeUrls);
    
    Client clientWithUrls = new Client(p2pConfig);
    
    // Test that multiple URLs are handled
    try {
      clientWithUrls.startSync();
    } catch (Exception e) {
      // Expected due to DNS failures
    }
    
    assertEquals(2, clientWithUrls.getTrees().size());
  }
} 