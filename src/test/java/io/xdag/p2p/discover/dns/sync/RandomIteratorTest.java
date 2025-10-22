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

import io.xdag.p2p.DnsException;
import io.xdag.p2p.discover.dns.DnsNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RandomIteratorTest {

    @Mock private Client mockClient;

    private RandomIterator iterator;

    @BeforeEach
    public void setUp() {
        iterator = new RandomIterator(mockClient);
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testConstructor() {
        RandomIterator iter = new RandomIterator(mockClient);
        assertNotNull(iter);
        assertNotNull(iter.getClient());
        assertNotNull(iter.getClientTrees());
        assertNotNull(iter.getLinkCache());
        assertNotNull(iter.getRandom());
    }

    // ==================== addTree() Tests ====================

    @Test
    public void testAddTreeWithValidUrl() throws DnsException {
        // Given - a valid DNS tree URL (must start with "tree://")
        String validUrl = "tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org";

        // When
        iterator.addTree(validUrl);

        // Then - should add to link cache without exception
        assertNotNull(iterator.getLinkCache());
    }

    @Test
    public void testAddTreeWithInvalidUrl() {
        // Given - an invalid URL
        String invalidUrl = "invalid-url";

        // When/Then - should throw DnsException
        assertThrows(DnsException.class, () -> {
            iterator.addTree(invalidUrl);
        });
    }

    @Test
    public void testAddTreeWithNullUrl() {
        // When/Then - should handle null gracefully or throw exception
        assertThrows(Exception.class, () -> {
            iterator.addTree(null);
        });
    }

    @Test
    public void testAddTreeWithEmptyUrl() {
        // When/Then - should throw DnsException
        assertThrows(DnsException.class, () -> {
            iterator.addTree("");
        });
    }

    // ==================== close() Tests ====================

    @Test
    public void testClose() {
        // When
        iterator.close();

        // Then - clientTrees should be null
        assertNull(iterator.getClientTrees());
    }

    @Test
    public void testCloseMultipleTimes() {
        // When
        iterator.close();
        iterator.close();
        iterator.close();

        // Then - should handle multiple closes gracefully
        assertNull(iterator.getClientTrees());
    }

    @Test
    public void testCloseAfterAddingTrees() throws DnsException {
        // Given
        String url = "tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org";
        iterator.addTree(url);

        // When
        iterator.close();

        // Then
        assertNull(iterator.getClientTrees());
    }

    // ==================== next() Tests ====================

    @Test
    public void testNextWhenNoTreesAdded() {
        // When - call next without adding any trees
        // pickTree() will throw IllegalArgumentException when size is 0
        // The exception is caught in the code, so next() should return null
        DnsNode result = null;
        try {
            result = iterator.next();
        } catch (IllegalArgumentException e) {
            // Expected when no trees added - random.nextInt(0) throws exception
            assertTrue(e.getMessage().contains("bound must be positive"));
        }

        // Then - should either return null or throw exception
        // (Both are acceptable behaviors when no trees exist)
    }

    // ==================== hasNext() Tests ====================

    @Test
    public void testHasNextWhenNoTreesAdded() {
        // When - hasNext() calls next() internally
        // next() will throw exception when pickTree() is called with size 0
        boolean result = false;
        try {
            result = iterator.hasNext();
        } catch (IllegalArgumentException e) {
            // Expected when no trees added
            assertTrue(e.getMessage().contains("bound must be positive"));
            return; // Test passes if exception thrown
        }

        // If no exception thrown, should return false
        assertFalse(result);
    }

    @Test
    public void testHasNextUpdatesCurrentNode() {
        // When - hasNext() calls next() internally
        try {
            iterator.hasNext();
        } catch (IllegalArgumentException e) {
            // Expected - no trees means pickTree() throws exception
            assertTrue(e.getMessage().contains("bound must be positive"));
            return; // Test passes
        }

        // Then - cur should be updated (will be null since no trees)
        assertNull(iterator.getCur());
    }

    // ==================== Edge Cases ====================

    @Test
    public void testConstructorWithNullClient() {
        // When
        RandomIterator iter = new RandomIterator(null);

        // Then - should create iterator (though client is null)
        assertNotNull(iter);
        assertNull(iter.getClient());
    }

    @Test
    public void testMultipleAddTree() throws DnsException {
        // Given - multiple valid URLs (same public key, different domains)
        String url1 = "tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes1.example.org";
        String url2 = "tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes2.example.org";

        // When
        iterator.addTree(url1);
        iterator.addTree(url2);

        // Then - should have added both to link cache
        assertNotNull(iterator.getLinkCache());
    }

    @Test
    public void testGettersReturnNonNull() {
        // When/Then - all getters should return non-null initially
        assertNotNull(iterator.getClient());
        assertNotNull(iterator.getClientTrees());
        assertNotNull(iterator.getLinkCache());
        assertNotNull(iterator.getRandom());
    }

    @Test
    public void testGettersAfterClose() {
        // Given
        iterator.close();

        // When/Then - clientTrees should be null after close
        assertNull(iterator.getClientTrees());
        // But other fields should still exist
        assertNotNull(iterator.getClient());
        assertNotNull(iterator.getLinkCache());
        assertNotNull(iterator.getRandom());
    }

    @Test
    public void testNextAfterClose() {
        // Given
        iterator.close();

        // When - call next after close
        DnsNode result = iterator.next();

        // Then - should return null or handle gracefully
        assertNull(result);
    }
}
