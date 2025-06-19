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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.discover.dns.tree.BranchEntry;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for SubtreeSync class. Tests DNS tree synchronization functionality. */
public class SubtreeSyncTest {

  private Client mockClient;
  private LinkEntry mockLinkEntry;
  private String testRoot;

  @BeforeEach
  public void setUp() {
    mockClient = mock(Client.class);
    mockLinkEntry = mock(LinkEntry.class);
    testRoot = "test-root-hash";
    
    when(mockLinkEntry.domain()).thenReturn("example.com");
  }

  @Test
  public void testConstructor() {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, true);

    assertEquals(mockClient, sync.client);
    assertEquals(mockLinkEntry, sync.linkEntry);
    assertEquals(testRoot, sync.root);
    assertTrue(sync.link);
    assertEquals(0, sync.leaves);
    assertNotNull(sync.missing);
    assertFalse(sync.missing.isEmpty());
    assertEquals(testRoot, sync.missing.peek());
  }

  @Test
  public void testConstructorWithNonLink() {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);

    assertFalse(sync.link);
    assertEquals(testRoot, sync.missing.peek());
  }

  @Test
  public void testDone() {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, true);

    // Initially not done (has root in missing)
    assertFalse(sync.done());

    // Clear missing list to simulate completion
    sync.missing.clear();
    assertTrue(sync.done());
  }

  @Test
  public void testResolveNextWithNodesEntry() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);
    NodesEntry mockNodesEntry = mock(NodesEntry.class);

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockNodesEntry);

    Entry result = sync.resolveNext(testRoot);

    assertEquals(mockNodesEntry, result);
    assertEquals(1, sync.leaves);
  }

  @Test
  public void testResolveNextWithLinkEntry() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, true);
    LinkEntry mockResolvedLinkEntry = mock(LinkEntry.class);

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockResolvedLinkEntry);

    Entry result = sync.resolveNext(testRoot);

    assertEquals(mockResolvedLinkEntry, result);
    assertEquals(1, sync.leaves);
  }

  @Test
  public void testResolveNextWithBranchEntry() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);
    BranchEntry mockBranchEntry = mock(BranchEntry.class);
    String[] children = {"child1", "child2", "child3"};

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockBranchEntry);
    when(mockBranchEntry.getChildren()).thenReturn(children);

    Entry result = sync.resolveNext(testRoot);

    assertEquals(mockBranchEntry, result);
    assertEquals(0, sync.leaves); // Branch entries don't increment leaves
    assertEquals(4, sync.missing.size()); // Original root + 3 children
  }

  @Test
  public void testResolveNextThrowsExceptionForNodesInLinkTree() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, true);
    NodesEntry mockNodesEntry = mock(NodesEntry.class);

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockNodesEntry);

    assertThrows(DnsException.class, () -> sync.resolveNext(testRoot));
  }

  @Test
  public void testResolveNextThrowsExceptionForLinkInNodesTree() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);
    LinkEntry mockResolvedLinkEntry = mock(LinkEntry.class);

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockResolvedLinkEntry);

    assertThrows(DnsException.class, () -> sync.resolveNext(testRoot));
  }

  @Test
  public void testResolveAll() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);
    NodesEntry mockNodesEntry = mock(NodesEntry.class);
    Map<String, Entry> dest = new HashMap<>();

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockNodesEntry);

    sync.resolveAll(dest);

    assertTrue(sync.done());
    assertEquals(1, dest.size());
    assertEquals(mockNodesEntry, dest.get(testRoot));
    assertEquals(1, sync.leaves);
  }

  @Test
  public void testResolveAllWithMultipleEntries() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);
    BranchEntry mockBranchEntry = mock(BranchEntry.class);
    NodesEntry mockNodesEntry1 = mock(NodesEntry.class);
    NodesEntry mockNodesEntry2 = mock(NodesEntry.class);
    String[] children = {"child1", "child2"};
    Map<String, Entry> dest = new HashMap<>();

    when(mockClient.resolveEntry(eq("example.com"), eq(testRoot)))
        .thenReturn(mockBranchEntry);
    when(mockBranchEntry.getChildren()).thenReturn(children);
    when(mockClient.resolveEntry(eq("example.com"), eq("child1")))
        .thenReturn(mockNodesEntry1);
    when(mockClient.resolveEntry(eq("example.com"), eq("child2")))
        .thenReturn(mockNodesEntry2);

    sync.resolveAll(dest);

    assertTrue(sync.done());
    assertEquals(3, dest.size()); // root branch + 2 children
    assertEquals(mockBranchEntry, dest.get(testRoot));
    assertEquals(mockNodesEntry1, dest.get("child1"));
    assertEquals(mockNodesEntry2, dest.get("child2"));
    assertEquals(2, sync.leaves); // Only nodes entries count as leaves
  }

  @Test
  public void testResolveAllWithClientException() throws Exception {
    SubtreeSync sync = new SubtreeSync(mockClient, mockLinkEntry, testRoot, false);

    when(mockClient.resolveEntry(anyString(), anyString()))
        .thenThrow(new UnknownHostException("DNS resolution failed"));

    assertThrows(UnknownHostException.class, () -> sync.resolveAll(new HashMap<>()));
  }
} 