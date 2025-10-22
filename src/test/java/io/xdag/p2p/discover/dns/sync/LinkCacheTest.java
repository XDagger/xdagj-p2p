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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkCacheTest {

    private LinkCache linkCache;

    @BeforeEach
    void setUp() {
        linkCache = new LinkCache();
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor() {
        LinkCache cache = new LinkCache();
        assertNotNull(cache.getBackrefs(), "Backrefs should be initialized");
        assertTrue(cache.getBackrefs().isEmpty(), "Backrefs should be empty initially");
        assertFalse(cache.isChanged(), "Changed flag should be false initially");
    }

    // ==================== isContainInOtherLink() Tests ====================

    @Test
    void testIsContainInOtherLinkWhenNotPresent() {
        assertFalse(linkCache.isContainInOtherLink("tree://nonexistent@example.com"),
            "Should return false for non-existent URL");
    }

    @Test
    void testIsContainInOtherLinkWhenPresentAndNotEmpty() {
        // Add a link first
        linkCache.addLink("tree://parent@example.com", "tree://child@example.com");

        assertTrue(linkCache.isContainInOtherLink("tree://child@example.com"),
            "Should return true when URL exists and has references");
    }

    @Test
    void testIsContainInOtherLinkWhenPresentButEmpty() {
        // Manually create an empty entry
        linkCache.getBackrefs().put("tree://empty@example.com", new HashSet<>());

        assertFalse(linkCache.isContainInOtherLink("tree://empty@example.com"),
            "Should return false when URL exists but has no references");
    }

    // ==================== addLink() Tests ====================

    @Test
    void testAddLinkFirstTime() {
        String parent = "tree://parent@example.com";
        String child = "tree://child@example.com";

        linkCache.addLink(parent, child);

        assertTrue(linkCache.isChanged(), "Changed flag should be true after adding first link");
        assertTrue(linkCache.isContainInOtherLink(child), "Child should be in cache");
        assertTrue(linkCache.getBackrefs().get(child).contains(parent),
            "Parent should be in child's references");
    }

    @Test
    void testAddLinkDuplicate() {
        String parent = "tree://parent@example.com";
        String child = "tree://child@example.com";

        // Add first time
        linkCache.addLink(parent, child);
        linkCache.setChanged(false); // Reset flag

        // Add same link again
        linkCache.addLink(parent, child);

        assertFalse(linkCache.isChanged(), "Changed flag should remain false for duplicate");
        assertEquals(1, linkCache.getBackrefs().get(child).size(),
            "Should have only one reference");
    }

    @Test
    void testAddLinkMultipleParents() {
        String child = "tree://child@example.com";
        String parent1 = "tree://parent1@example.com";
        String parent2 = "tree://parent2@example.com";
        String parent3 = "tree://parent3@example.com";

        linkCache.addLink(parent1, child);
        linkCache.addLink(parent2, child);
        linkCache.addLink(parent3, child);

        Set<String> refs = linkCache.getBackrefs().get(child);
        assertEquals(3, refs.size(), "Child should have 3 parent references");
        assertTrue(refs.contains(parent1), "Should contain parent1");
        assertTrue(refs.contains(parent2), "Should contain parent2");
        assertTrue(refs.contains(parent3), "Should contain parent3");
    }

    @Test
    void testAddLinkMultipleChildren() {
        String parent = "tree://parent@example.com";
        String child1 = "tree://child1@example.com";
        String child2 = "tree://child2@example.com";
        String child3 = "tree://child3@example.com";

        linkCache.addLink(parent, child1);
        linkCache.addLink(parent, child2);
        linkCache.addLink(parent, child3);

        assertEquals(3, linkCache.getBackrefs().size(), "Should have 3 children");
        assertTrue(linkCache.isContainInOtherLink(child1), "Child1 should be in cache");
        assertTrue(linkCache.isContainInOtherLink(child2), "Child2 should be in cache");
        assertTrue(linkCache.isContainInOtherLink(child3), "Child3 should be in cache");
    }

    // ==================== resetLinks() Tests ====================

    @Test
    void testResetLinksSimple() {
        String parent = "tree://parent@example.com";
        String child = "tree://child@example.com";

        linkCache.addLink(parent, child);
        linkCache.setChanged(false);

        // Reset links from parent
        linkCache.resetLinks(parent, null);

        assertTrue(linkCache.isChanged(), "Changed flag should be true after reset");
        assertFalse(linkCache.isContainInOtherLink(child), "Child should be removed");
        assertFalse(linkCache.getBackrefs().containsKey(child), "Child key should be removed");
    }

    @Test
    void testResetLinksWithKeep() {
        String parent = "tree://parent@example.com";
        String child1 = "tree://child1@example.com";
        String child2 = "tree://child2@example.com";

        linkCache.addLink(parent, child1);
        linkCache.addLink(parent, child2);
        linkCache.setChanged(false);

        // Reset but keep child1
        Set<String> keep = new HashSet<>();
        keep.add(child1);
        linkCache.resetLinks(parent, keep);

        assertTrue(linkCache.isContainInOtherLink(child1), "Child1 should be kept");
        assertFalse(linkCache.isContainInOtherLink(child2), "Child2 should be removed");
    }

    @Test
    void testResetLinksWithMultipleParents() {
        String child = "tree://child@example.com";
        String parent1 = "tree://parent1@example.com";
        String parent2 = "tree://parent2@example.com";

        linkCache.addLink(parent1, child);
        linkCache.addLink(parent2, child);
        linkCache.setChanged(false);

        // Reset links from parent1
        linkCache.resetLinks(parent1, null);

        assertTrue(linkCache.isContainInOtherLink(child),
            "Child should still exist (referenced by parent2)");
        assertEquals(1, linkCache.getBackrefs().get(child).size(),
            "Child should have 1 reference");
        assertTrue(linkCache.getBackrefs().get(child).contains(parent2),
            "Child should still reference parent2");
    }

    @Test
    void testResetLinksRecursive() {
        // Create a chain: parent -> child1 -> child2 -> child3
        String parent = "tree://parent@example.com";
        String child1 = "tree://child1@example.com";
        String child2 = "tree://child2@example.com";
        String child3 = "tree://child3@example.com";

        linkCache.addLink(parent, child1);
        linkCache.addLink(child1, child2);
        linkCache.addLink(child2, child3);
        linkCache.setChanged(false);

        // Reset from parent - should remove all in chain
        linkCache.resetLinks(parent, null);

        assertTrue(linkCache.isChanged(), "Changed flag should be true");
        assertFalse(linkCache.isContainInOtherLink(child1), "Child1 should be removed");
        assertFalse(linkCache.isContainInOtherLink(child2), "Child2 should be removed");
        assertFalse(linkCache.isContainInOtherLink(child3), "Child3 should be removed");
        assertTrue(linkCache.getBackrefs().isEmpty(), "All backrefs should be empty");
    }

    @Test
    void testResetLinksComplexGraph() {
        // Create a more complex structure:
        //   parent -> child1 -> child2
        //   parent -> child3
        //   other -> child2 (child2 has 2 parents)
        String parent = "tree://parent@example.com";
        String other = "tree://other@example.com";
        String child1 = "tree://child1@example.com";
        String child2 = "tree://child2@example.com";
        String child3 = "tree://child3@example.com";

        linkCache.addLink(parent, child1);
        linkCache.addLink(child1, child2);
        linkCache.addLink(parent, child3);
        linkCache.addLink(other, child2); // child2 has another parent
        linkCache.setChanged(false);

        // Reset from parent
        linkCache.resetLinks(parent, null);

        // child2 should still exist because it's referenced by 'other'
        assertTrue(linkCache.isContainInOtherLink(child2),
            "Child2 should still exist (referenced by other)");
        assertFalse(linkCache.isContainInOtherLink(child1), "Child1 should be removed");
        assertFalse(linkCache.isContainInOtherLink(child3), "Child3 should be removed");
    }

    @Test
    void testResetLinksEmptyCache() {
        linkCache.setChanged(false);

        // Reset on empty cache should not throw
        linkCache.resetLinks("tree://nonexistent@example.com", null);

        assertFalse(linkCache.isChanged(), "Changed flag should remain false");
        assertTrue(linkCache.getBackrefs().isEmpty(), "Cache should still be empty");
    }

    // ==================== Changed Flag Tests ====================

    @Test
    void testChangedFlagInitialState() {
        assertFalse(linkCache.isChanged(), "Changed flag should be false initially");
    }

    @Test
    void testChangedFlagAfterAddLink() {
        linkCache.addLink("tree://parent@example.com", "tree://child@example.com");
        assertTrue(linkCache.isChanged(), "Changed flag should be true after adding link");
    }

    @Test
    void testChangedFlagManualSet() {
        linkCache.setChanged(true);
        assertTrue(linkCache.isChanged(), "Changed flag should be true after manual set");

        linkCache.setChanged(false);
        assertFalse(linkCache.isChanged(), "Changed flag should be false after manual reset");
    }

    // ==================== Edge Cases ====================

    @Test
    void testGetBackrefsReturnsActualMap() {
        Map<String, Set<String>> backrefs1 = linkCache.getBackrefs();
        Map<String, Set<String>> backrefs2 = linkCache.getBackrefs();

        assertSame(backrefs1, backrefs2, "Should return the same map instance");
    }

    @Test
    void testAddLinkWithNullParent() {
        // This tests behavior - may throw NPE or handle gracefully
        // Based on code, it will add null as parent
        linkCache.addLink(null, "tree://child@example.com");

        assertTrue(linkCache.isContainInOtherLink("tree://child@example.com"));
        assertTrue(linkCache.getBackrefs().get("tree://child@example.com").contains(null));
    }

    @Test
    void testAddLinkWithNullChild() {
        // This tests behavior with null child
        linkCache.addLink("tree://parent@example.com", null);

        assertTrue(linkCache.getBackrefs().containsKey(null));
    }

    @Test
    void testResetLinksWithEmptyKeepSet() {
        String parent = "tree://parent@example.com";
        String child = "tree://child@example.com";

        linkCache.addLink(parent, child);
        linkCache.setChanged(false);

        // Reset with empty keep set (same as null keep)
        linkCache.resetLinks(parent, new HashSet<>());

        assertFalse(linkCache.isContainInOtherLink(child), "Child should be removed");
    }

    @Test
    void testLargeNumberOfLinks() {
        String parent = "tree://parent@example.com";

        // Add 1000 links
        for (int i = 0; i < 1000; i++) {
            linkCache.addLink(parent, "tree://child" + i + "@example.com");
        }

        assertEquals(1000, linkCache.getBackrefs().size(),
            "Should have 1000 children");

        // Reset all
        linkCache.resetLinks(parent, null);

        assertTrue(linkCache.getBackrefs().isEmpty(),
            "All children should be removed");
    }
}
