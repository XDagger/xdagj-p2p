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
package io.xdag.p2p.discover.kad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.discover.kad.table.NodeTable;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class DiscoverTaskTest {

    private KadService kadService;
    private DiscoverTask discoverTask;
    private NodeTable mockTable;
    private NodeHandler mockNodeHandler;

    @BeforeEach
    void setUp() {
        kadService = Mockito.mock(KadService.class);
        mockTable = Mockito.mock(NodeTable.class);
        mockNodeHandler = Mockito.mock(NodeHandler.class);

        when(kadService.getTable()).thenReturn(mockTable);

        discoverTask = new DiscoverTask(kadService);
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor() {
        assertNotNull(discoverTask, "DiscoverTask should be created successfully");
    }

    // ==================== nextTargetId Tests ====================

    @Test
    void testNextTargetIdReturnsRandomWhenHomeIdEmpty() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(null);
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes id1 = discoverTask.nextTargetId();
        Bytes id2 = discoverTask.nextTargetId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertFalse(id1.isEmpty());
        assertFalse(id2.isEmpty());
        assertEquals(20, id1.size(), "Node ID should be 20 bytes (160 bits)");
        assertEquals(20, id2.size(), "Node ID should be 20 bytes (160 bits)");
    }

    @Test
    void testNextTargetIdReturnsHomeIdEveryMaxLoop() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes last = null;
        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM - 1; i++) {
            last = discoverTask.nextTargetId();
        }
        Bytes same = discoverTask.nextTargetId();

        assertNotNull(same);
        assertEquals(home.getId(), same.toUnprefixedHexString());
        assertNotNull(last);
        assertNotEquals(same, last);
    }

    @Test
    void testLoopCounterResetsAfterHomeId() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM; i++) {
            discoverTask.nextTargetId();
        }
        Bytes next = discoverTask.nextTargetId();
        assertNotNull(next);
    }

    @Test
    void testNextTargetIdGeneratesDifferentRandomIds() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId("");
        when(kadService.getPublicHomeNode()).thenReturn(home);

        List<Bytes> generatedIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            generatedIds.add(discoverTask.nextTargetId());
        }

        // Check that IDs are unique (very high probability with random 160-bit values)
        long uniqueCount = generatedIds.stream().distinct().count();
        assertTrue(uniqueCount >= 9, "Should generate mostly unique random IDs");
    }

    @Test
    void testNextTargetIdWith160BitHomeId() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        Bytes homeId = Bytes.random(20); // 160 bits
        home.setId(homeId.toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);

        // Call MAX_LOOP_NUM times to trigger home ID return
        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM - 1; i++) {
            discoverTask.nextTargetId();
        }

        Bytes returnedId = discoverTask.nextTargetId();
        assertEquals(homeId.toUnprefixedHexString(), returnedId.toUnprefixedHexString());
    }

    // ==================== init Tests ====================

    @Test
    void testInitCreatesScheduledTask() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);
        when(mockTable.getClosestNodes(any())).thenReturn(new ArrayList<>());

        discoverTask.init();

        // Wait a bit for the scheduled task to execute at least once
        Thread.sleep(100);

        discoverTask.close();
    }

    @Test
    void testInitWithValidKadService() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);
        when(mockTable.getClosestNodes(any())).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> {
            discoverTask.init();
            discoverTask.close();
        }, "init() should not throw exception with valid kadService");
    }

    // ==================== close Tests ====================

    @Test
    void testClose() {
        assertDoesNotThrow(() -> {
            discoverTask.close();
        }, "close() should not throw exception");
    }

    @Test
    void testCloseAfterInit() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);
        when(mockTable.getClosestNodes(any())).thenReturn(new ArrayList<>());

        discoverTask.init();
        Thread.sleep(50);

        assertDoesNotThrow(() -> {
            discoverTask.close();
        }, "close() should shutdown executor gracefully");
    }

    @Test
    void testMultipleCloseCallsAreSafe() {
        discoverTask.close();
        discoverTask.close();
        discoverTask.close();

        // Should not throw exception
    }

    // ==================== Integration Tests ====================

    @Test
    void testDiscoverTaskWithNoNodes() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);
        when(mockTable.getClosestNodes(any())).thenReturn(new ArrayList<>());

        discoverTask.init();
        Thread.sleep(100);
        discoverTask.close();

        // Should handle empty node list gracefully
        verify(mockTable, atLeastOnce()).getClosestNodes(any());
    }

    @Test
    void testDiscoverTaskWithSingleNode() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);

        Node targetNode = new Node(Bytes.random(20).toUnprefixedHexString(), "192.168.1.100", null, 30303);
        when(mockTable.getClosestNodes(any())).thenReturn(Arrays.asList(targetNode));
        when(kadService.getNodeHandler(any())).thenReturn(mockNodeHandler);

        discoverTask.init();
        Thread.sleep(200);
        discoverTask.close();

        // Should attempt to send findNode to the target
        verify(kadService, atLeastOnce()).getNodeHandler(any());
    }

    @Test
    void testDiscoverTaskWithMultipleNodes() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(new Node(Bytes.random(20).toUnprefixedHexString(),
                              "192.168.1." + (100 + i), null, 30303));
        }
        when(mockTable.getClosestNodes(any())).thenReturn(nodes);
        when(kadService.getNodeHandler(any())).thenReturn(mockNodeHandler);

        discoverTask.init();
        Thread.sleep(200);
        discoverTask.close();

        // Should query the table for closest nodes
        verify(mockTable, atLeastOnce()).getClosestNodes(any());
    }

    // ==================== Edge Cases ====================

    @Test
    void testNextTargetIdWithEmptyStringHomeId() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId("");
        when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes id = discoverTask.nextTargetId();

        assertNotNull(id);
        assertEquals(20, id.size(), "Should generate 20-byte random ID");
    }

    @Test
    void testNextTargetIdWithWhitespaceHomeId() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId("   ");
        when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes id = discoverTask.nextTargetId();

        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    void testNextTargetIdConsistencyAcrossMultipleCycles() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        Bytes homeId = Bytes.random(20);
        home.setId(homeId.toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);

        // First cycle - should return home ID at MAX_LOOP_NUM
        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM - 1; i++) {
            discoverTask.nextTargetId();
        }
        Bytes firstCycleHomeId = discoverTask.nextTargetId();

        // Second cycle - should return home ID at MAX_LOOP_NUM again
        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM - 1; i++) {
            discoverTask.nextTargetId();
        }
        Bytes secondCycleHomeId = discoverTask.nextTargetId();

        assertEquals(firstCycleHomeId, secondCycleHomeId,
                    "Home ID should be consistent across cycles");
    }

    // ==================== Exception Handling Tests ====================

    @Test
    void testDiscoverTaskHandlesNodeHandlerException() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);

        Node targetNode = new Node(Bytes.random(20).toUnprefixedHexString(), "192.168.1.100", null, 30303);
        when(mockTable.getClosestNodes(any())).thenReturn(Arrays.asList(targetNode));
        when(kadService.getNodeHandler(any())).thenThrow(new RuntimeException("Test exception"));

        discoverTask.init();
        Thread.sleep(200);
        discoverTask.close();

        // Should handle exception gracefully and continue running
        verify(mockTable, atLeastOnce()).getClosestNodes(any());
    }

    @Test
    void testDiscoverTaskHandlesTableException() throws InterruptedException {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        when(kadService.getPublicHomeNode()).thenReturn(home);
        when(mockTable.getClosestNodes(any())).thenThrow(new RuntimeException("Table error"));

        discoverTask.init();
        Thread.sleep(200);
        discoverTask.close();

        // Should handle exception and continue
    }
}

