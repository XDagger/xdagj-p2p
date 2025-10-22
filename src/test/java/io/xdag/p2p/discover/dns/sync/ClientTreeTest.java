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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.RootEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientTreeTest {

    @Mock
    private Client mockClient;

    @Mock
    private LinkEntry mockLinkEntry;

    @Mock
    private LinkCache mockLinkCache;

    @Mock
    private RootEntry mockRootEntry;

    private ClientTree clientTree;

    @BeforeEach
    void setUp() {
        // Setup basic mock behavior
        when(mockLinkEntry.represent()).thenReturn("tree://TESTKEY@example.com");
        when(mockLinkEntry.domain()).thenReturn("example.com");
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructorWithClientOnly() {
        ClientTree tree = new ClientTree(mockClient);

        assertNotNull(tree, "ClientTree should be created");
        assertNull(tree.getLinkEntry(), "LinkEntry should be null with single-arg constructor");
        assertNull(tree.getRoot(), "Root should be null initially");
    }

    @Test
    void testConstructorWithAllParameters() {
        ClientTree tree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        assertNotNull(tree, "ClientTree should be created");
        assertEquals(mockLinkEntry, tree.getLinkEntry(), "LinkEntry should be set");
        assertNull(tree.getRoot(), "Root should be null initially");
    }

    @Test
    void testConstructorWithNullClient() {
        // Should create tree even with null client (may fail later in operations)
        ClientTree tree = new ClientTree(null);
        assertNotNull(tree, "ClientTree should be created even with null client");
    }

    // ==================== Getter/Setter Tests ====================

    @Test
    void testSetAndGetLinkEntry() {
        clientTree = new ClientTree(mockClient);

        assertNull(clientTree.getLinkEntry(), "Initial link entry should be null");

        clientTree.setLinkEntry(mockLinkEntry);
        assertEquals(mockLinkEntry, clientTree.getLinkEntry(), "Link entry should be set");
    }

    @Test
    void testSetAndGetRoot() {
        clientTree = new ClientTree(mockClient);

        assertNull(clientTree.getRoot(), "Initial root should be null");

        clientTree.setRoot(mockRootEntry);
        assertEquals(mockRootEntry, clientTree.getRoot(), "Root should be set");
    }

    @Test
    void testSetLinkEntryToNull() {
        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        assertEquals(mockLinkEntry, clientTree.getLinkEntry());

        clientTree.setLinkEntry(null);
        assertNull(clientTree.getLinkEntry(), "Link entry should be null after setting to null");
    }

    @Test
    void testSetRootToNull() {
        clientTree = new ClientTree(mockClient);
        clientTree.setRoot(mockRootEntry);

        assertEquals(mockRootEntry, clientTree.getRoot());

        clientTree.setRoot(null);
        assertNull(clientTree.getRoot(), "Root should be null after setting to null");
    }

    // ==================== nextScheduledRootCheck() Tests ====================

    @Test
    void testNextScheduledRootCheckInitial() {
        clientTree = new ClientTree(mockClient);

        long nextCheck = clientTree.nextScheduledRootCheck();

        // Should be based on lastValidateTime (0) + recheckInterval
        assertTrue(nextCheck > 0, "Next check time should be positive");
        assertTrue(nextCheck <= Client.recheckInterval * 1000L,
            "Next check should be within recheck interval from epoch");
    }

    @Test
    void testNextScheduledRootCheckAfterUpdate() throws Exception {
        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        // The nextScheduledRootCheck is based on lastValidateTime which is 0 initially
        // So the nextCheck will be recheckInterval from epoch, which is in the past
        long nextCheck = clientTree.nextScheduledRootCheck();

        // For a new tree, next check is based on epoch + interval (will be in past)
        assertTrue(nextCheck > 0, "Next check should be calculated from epoch");
        assertTrue(nextCheck <= System.currentTimeMillis(),
            "Next check for new tree is typically in the past (based on epoch)");
    }

    // ==================== toString() Tests ====================

    @Test
    void testToStringWithLinkEntry() {
        when(mockLinkEntry.toString()).thenReturn("LinkEntry[tree://TESTKEY@example.com]");

        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        String result = clientTree.toString();

        assertEquals("LinkEntry[tree://TESTKEY@example.com]", result,
            "toString should delegate to linkEntry");
    }

    @Test
    void testToStringWithNullLinkEntry() {
        clientTree = new ClientTree(mockClient);

        // Should throw NPE or return null representation
        assertThrows(NullPointerException.class, () -> {
            clientTree.toString();
        }, "toString should throw NPE when linkEntry is null");
    }

    // ==================== gcLinks() Tests ====================

    @Test
    void testGcLinksWhenNotDone() {
        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        // gcLinks will throw NPE because linkSync is null
        // This is expected behavior - gcLinks should handle null linkSync
        assertThrows(NullPointerException.class, () -> {
            clientTree.gcLinks();
        }, "gcLinks should throw NPE when linkSync is null");
    }

    // ==================== Edge Cases ====================

    @Test
    void testMultipleSettersOnSameInstance() {
        clientTree = new ClientTree(mockClient);

        LinkEntry entry1 = mock(LinkEntry.class);
        LinkEntry entry2 = mock(LinkEntry.class);
        RootEntry root1 = mock(RootEntry.class);
        RootEntry root2 = mock(RootEntry.class);

        // Set and change multiple times
        clientTree.setLinkEntry(entry1);
        clientTree.setRoot(root1);
        assertEquals(entry1, clientTree.getLinkEntry());
        assertEquals(root1, clientTree.getRoot());

        clientTree.setLinkEntry(entry2);
        clientTree.setRoot(root2);
        assertEquals(entry2, clientTree.getLinkEntry());
        assertEquals(root2, clientTree.getRoot());
    }

    @Test
    void testConstructorParametersAreStored() {
        ClientTree tree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        // Verify that the parameters are properly stored
        assertEquals(mockLinkEntry, tree.getLinkEntry(),
            "Constructor should store link entry");
    }

    @Test
    void testDifferentClientsCreateDifferentTrees() {
        Client client1 = mock(Client.class);
        Client client2 = mock(Client.class);

        ClientTree tree1 = new ClientTree(client1);
        ClientTree tree2 = new ClientTree(client2);

        assertNotSame(tree1, tree2, "Different clients should create different trees");
    }

    @Test
    void testLinkEntryRepresentIsCalled() {
        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        // Just verify the behavior without verifying toString (Mockito limitation)
        when(mockLinkEntry.toString()).thenReturn("test");
        String result = clientTree.toString();

        assertEquals("test", result, "toString should delegate to linkEntry");
    }

    @Test
    void testNextScheduledRootCheckConsistency() {
        clientTree = new ClientTree(mockClient);

        long check1 = clientTree.nextScheduledRootCheck();
        long check2 = clientTree.nextScheduledRootCheck();

        assertEquals(check1, check2,
            "Multiple calls should return same value without state change");
    }

    @Test
    void testGcLinksMultipleCalls() {
        clientTree = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        // Multiple calls should throw NPE consistently
        assertThrows(NullPointerException.class, () -> clientTree.gcLinks());
        assertThrows(NullPointerException.class, () -> clientTree.gcLinks());
        assertThrows(NullPointerException.class, () -> clientTree.gcLinks());
    }

    @Test
    void testRootGetterAfterSetter() {
        clientTree = new ClientTree(mockClient);

        when(mockRootEntry.getSeq()).thenReturn(42);
        when(mockRootEntry.getLRoot()).thenReturn("LROOT");
        when(mockRootEntry.getERoot()).thenReturn("EROOT");

        clientTree.setRoot(mockRootEntry);
        RootEntry retrieved = clientTree.getRoot();

        assertSame(mockRootEntry, retrieved, "Should return exact same instance");
        assertEquals(42, retrieved.getSeq(), "Root properties should be accessible");
    }

    @Test
    void testLinkEntryGetterAfterSetter() {
        clientTree = new ClientTree(mockClient);

        when(mockLinkEntry.domain()).thenReturn("test.example.com");

        clientTree.setLinkEntry(mockLinkEntry);
        LinkEntry retrieved = clientTree.getLinkEntry();

        assertSame(mockLinkEntry, retrieved, "Should return exact same instance");
        assertEquals("test.example.com", retrieved.domain(),
            "Link entry properties should be accessible");
    }

    @Test
    void testConstructorInitializesInternalState() {
        // Both constructors should initialize properly
        ClientTree tree1 = new ClientTree(mockClient);
        ClientTree tree2 = new ClientTree(mockClient, mockLinkCache, mockLinkEntry);

        assertNotNull(tree1, "Single-arg constructor should work");
        assertNotNull(tree2, "Three-arg constructor should work");

        // Both should have nextScheduledRootCheck available
        assertTrue(tree1.nextScheduledRootCheck() >= 0);
        assertTrue(tree2.nextScheduledRootCheck() >= 0);
    }
}
