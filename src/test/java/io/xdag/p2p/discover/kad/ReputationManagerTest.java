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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ReputationManager.
 *
 * Tests cover:
 * - Basic CRUD operations
 * - Persistence (save/load)
 * - Time-based decay
 * - Atomic file operations
 * - Concurrent access
 * - Error handling
 */
class ReputationManagerTest {

    @TempDir
    Path tempDir;

    private ReputationManager reputationManager;

    @BeforeEach
    void setUp() {
        // Use short save interval for faster tests
        reputationManager = new ReputationManager(tempDir.toString(), 100);
    }

    @AfterEach
    void tearDown() {
        if (reputationManager != null) {
            reputationManager.stop();
        }
    }

    // ========== Basic Operations ==========

    @Test
    @DisplayName("Should return default reputation for unknown node")
    void testGetReputation_UnknownNode_ReturnsDefault() {
        // When
        int reputation = reputationManager.getReputation("unknown-node");

        // Then
        assertEquals(100, reputation, "Unknown node should have default reputation of 100");
    }

    @Test
    @DisplayName("Should set and get reputation correctly")
    void testSetAndGetReputation() {
        // Given
        String nodeId = "test-node-1";
        int expectedReputation = 150;

        // When
        reputationManager.setReputation(nodeId, expectedReputation);
        int actualReputation = reputationManager.getReputation(nodeId);

        // Then
        assertEquals(expectedReputation, actualReputation);
    }

    @Test
    @DisplayName("Should update reputation for existing node")
    void testUpdateReputation() {
        // Given
        String nodeId = "test-node-2";
        reputationManager.setReputation(nodeId, 120);

        // When
        reputationManager.setReputation(nodeId, 180);
        int reputation = reputationManager.getReputation(nodeId);

        // Then
        assertEquals(180, reputation, "Reputation should be updated");
    }

    @Test
    @DisplayName("Should track multiple nodes independently")
    void testMultipleNodes() {
        // Given
        String node1 = "node-1";
        String node2 = "node-2";
        String node3 = "node-3";

        // When
        reputationManager.setReputation(node1, 150);
        reputationManager.setReputation(node2, 75);
        reputationManager.setReputation(node3, 100);

        // Then
        assertEquals(150, reputationManager.getReputation(node1));
        assertEquals(75, reputationManager.getReputation(node2));
        assertEquals(100, reputationManager.getReputation(node3));
    }

    @Test
    @DisplayName("Should return correct size")
    void testSize() {
        // Given
        assertEquals(0, reputationManager.size(), "Initial size should be 0");

        // When
        reputationManager.setReputation("node-1", 100);
        reputationManager.setReputation("node-2", 120);
        reputationManager.setReputation("node-3", 80);

        // Then
        assertEquals(3, reputationManager.size(), "Size should be 3 after adding 3 nodes");
    }

    @Test
    @DisplayName("Should clear all reputation data")
    void testClear() {
        // Given
        reputationManager.setReputation("node-1", 150);
        reputationManager.setReputation("node-2", 75);
        assertEquals(2, reputationManager.size());

        // When
        reputationManager.clear();

        // Then
        assertEquals(0, reputationManager.size(), "Size should be 0 after clear");
        assertEquals(100, reputationManager.getReputation("node-1"), "Cleared node should return default reputation");
    }

    // ========== Persistence Tests ==========

    @Test
    @DisplayName("Should persist and load reputation data")
    void testPersistence() throws InterruptedException {
        // Given
        String node1 = "persistent-node-1";
        String node2 = "persistent-node-2";
        reputationManager.setReputation(node1, 175);
        reputationManager.setReputation(node2, 60);

        // When - wait for auto-save
        Thread.sleep(200);

        // Verify file exists
        Path reputationFile = tempDir.resolve("reputation.dat");
        assertTrue(Files.exists(reputationFile), "Reputation file should exist after auto-save");

        // Create new manager and load
        reputationManager.stop();
        ReputationManager newManager = new ReputationManager(tempDir.toString(), 1000);

        // Then
        assertEquals(175, newManager.getReputation(node1), "Node 1 reputation should be loaded");
        assertEquals(60, newManager.getReputation(node2), "Node 2 reputation should be loaded");
        assertEquals(2, newManager.size(), "Should load 2 nodes");

        newManager.stop();
    }

    @Test
    @DisplayName("Should save immediately on stop")
    void testSaveOnStop() throws InterruptedException {
        // Given
        String nodeId = "immediate-save-node";
        reputationManager.setReputation(nodeId, 130);

        // Wait for at least one auto-save first to create the file
        Thread.sleep(150);

        // When
        reputationManager.stop();

        // Then
        Path reputationFile = tempDir.resolve("reputation.dat");
        assertTrue(Files.exists(reputationFile), "Reputation file should exist after stop");

        // Load and verify
        ReputationManager newManager = new ReputationManager(tempDir.toString(), 1000);
        assertEquals(130, newManager.getReputation(nodeId), "Reputation should be saved on stop");
        newManager.stop();
    }

    @Test
    @DisplayName("Should create backup file on save")
    void testBackupCreation() throws InterruptedException {
        // Given
        reputationManager.setReputation("backup-test", 140);

        // When - wait for first save
        Thread.sleep(200);

        // Modify and wait for second save
        reputationManager.setReputation("backup-test", 160);
        Thread.sleep(200);

        // Then
        Path backupFile = tempDir.resolve("reputation.dat.bak");
        assertTrue(Files.exists(backupFile), "Backup file should be created");
    }

    @Test
    @DisplayName("Should recover from backup if main file missing")
    void testRecoverFromBackup() throws IOException, InterruptedException {
        // Given - create initial data and wait for TWO saves (backup created on second save)
        reputationManager.setReputation("backup-recovery", 155);
        Thread.sleep(150); // Wait for first save

        reputationManager.setReputation("backup-recovery", 155); // Trigger second save
        Thread.sleep(150); // Wait for second save (which creates backup)

        // When - delete main file, keep backup
        Path mainFile = tempDir.resolve("reputation.dat");
        Path backupFile = tempDir.resolve("reputation.dat.bak");

        reputationManager.stop();
        Files.deleteIfExists(mainFile);
        assertTrue(Files.exists(backupFile), "Backup should exist after two saves");

        // Load from backup
        ReputationManager recoveredManager = new ReputationManager(tempDir.toString(), 1000);

        // Then
        assertEquals(155, recoveredManager.getReputation("backup-recovery"),
            "Should recover reputation from backup");

        recoveredManager.stop();
    }

    @Test
    @DisplayName("Should handle empty reputation data on save")
    void testSaveEmptyData() throws InterruptedException {
        // Given - empty manager
        assertEquals(0, reputationManager.size());

        // When
        Thread.sleep(200); // Wait for auto-save attempt

        // Then - should not crash, file may not be created
        assertTrue(reputationManager.size() == 0, "Manager should still be empty");
    }

    // ========== Reputation Decay Tests ==========

    @Test
    @DisplayName("Should not decay reputation within first day")
    void testNoDecayWithinFirstDay() {
        // Given
        String nodeId = "no-decay-node";
        reputationManager.setReputation(nodeId, 150);

        // When - immediately get (no time passed)
        int reputation = reputationManager.getReputation(nodeId);

        // Then
        assertEquals(150, reputation, "Reputation should not decay within first day");
    }

    @Test
    @DisplayName("Should handle extreme reputation values")
    void testExtremeReputationValues() {
        // Given
        String highNode = "high-rep";
        String lowNode = "low-rep";
        String normalNode = "normal-rep";

        // When
        reputationManager.setReputation(highNode, 200);
        reputationManager.setReputation(lowNode, 0);
        reputationManager.setReputation(normalNode, 100);

        // Then
        assertEquals(200, reputationManager.getReputation(highNode));
        assertEquals(0, reputationManager.getReputation(lowNode));
        assertEquals(100, reputationManager.getReputation(normalNode));
    }

    // ========== Concurrency Tests ==========

    @Test
    @DisplayName("Should handle concurrent reads and writes")
    void testConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // When - multiple threads updating different nodes
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String nodeId = "thread-" + threadId + "-node-" + j;
                    reputationManager.setReputation(nodeId, 100 + j);
                    reputationManager.getReputation(nodeId);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        assertEquals(threadCount * operationsPerThread, reputationManager.size(),
            "All nodes should be stored without data loss");
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle null node ID gracefully")
    void testNullNodeId() {
        // When/Then - ConcurrentHashMap doesn't accept null keys
        // Both get and set should throw NPE - this is expected behavior
        assertThrows(NullPointerException.class, () -> {
            reputationManager.setReputation(null, 150);
        }, "Setting null node ID should throw NullPointerException");

        assertThrows(NullPointerException.class, () -> {
            reputationManager.getReputation(null);
        }, "Getting null node ID should throw NullPointerException");
    }

    @Test
    @DisplayName("Should handle empty node ID")
    void testEmptyNodeId() {
        // When
        reputationManager.setReputation("", 120);
        int reputation = reputationManager.getReputation("");

        // Then
        assertEquals(120, reputation, "Should handle empty string node ID");
    }

    @Test
    @DisplayName("Should handle very long node ID")
    void testLongNodeId() {
        // Given
        String longNodeId = "a".repeat(1000);

        // When
        reputationManager.setReputation(longNodeId, 130);
        int reputation = reputationManager.getReputation(longNodeId);

        // Then
        assertEquals(130, reputation, "Should handle very long node IDs");
    }

    @Test
    @DisplayName("Should handle rapid start/stop cycles")
    void testRapidStartStop() throws InterruptedException {
        // When
        for (int i = 0; i < 5; i++) {
            reputationManager.setReputation("rapid-test-" + i, 100 + i * 10);
            Thread.sleep(50);
        }

        reputationManager.stop();
        Thread.sleep(100);

        // Start new manager
        ReputationManager newManager = new ReputationManager(tempDir.toString(), 100);

        // Then - should load previous data
        assertTrue(newManager.size() > 0, "Should load data after rapid cycles");

        newManager.stop();
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should work with realistic node discovery scenario")
    void testRealisticScenario() throws InterruptedException {
        // Simulate node discovery workflow

        // Day 1: Discover 5 new nodes, all start at neutral
        for (int i = 1; i <= 5; i++) {
            String nodeId = "real-node-" + i;
            reputationManager.setReputation(nodeId, 100);
        }

        // Some nodes respond well (+20)
        reputationManager.setReputation("real-node-1", 120);
        reputationManager.setReputation("real-node-2", 120);

        // Some nodes timeout (-10)
        reputationManager.setReputation("real-node-3", 90);

        // Some nodes are excellent (+40)
        reputationManager.setReputation("real-node-4", 140);

        // Wait for auto-save
        Thread.sleep(200);

        // Simulate restart
        reputationManager.stop();
        ReputationManager afterRestart = new ReputationManager(tempDir.toString(), 100);

        // Verify all reputations persisted correctly
        assertEquals(120, afterRestart.getReputation("real-node-1"));
        assertEquals(120, afterRestart.getReputation("real-node-2"));
        assertEquals(90, afterRestart.getReputation("real-node-3"));
        assertEquals(140, afterRestart.getReputation("real-node-4"));
        assertEquals(100, afterRestart.getReputation("real-node-5"));

        afterRestart.stop();
    }

    @Test
    @DisplayName("Should maintain data integrity over multiple save cycles")
    void testDataIntegrityOverTime() throws InterruptedException {
        // Given
        String nodeId = "integrity-test";
        int[] reputations = {100, 120, 140, 160, 150, 130};

        // When - update reputation multiple times with saves in between
        for (int reputation : reputations) {
            reputationManager.setReputation(nodeId, reputation);
            Thread.sleep(150); // Wait for auto-save
        }

        // Then - final value should be correct
        assertEquals(130, reputationManager.getReputation(nodeId),
            "Final reputation should match last update");

        // Verify persistence
        reputationManager.stop();
        ReputationManager newManager = new ReputationManager(tempDir.toString(), 1000);
        assertEquals(130, newManager.getReputation(nodeId),
            "Persisted reputation should match");

        newManager.stop();
    }
}
