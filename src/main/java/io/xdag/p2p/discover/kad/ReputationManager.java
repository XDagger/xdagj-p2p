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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Manages node reputation persistence to disk.
 *
 * <p>Features:
 * <ul>
 *   <li>Automatic periodic saves</li>
 *   <li>Atomic file operations with backup</li>
 *   <li>Reputation decay over time</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 */
@Slf4j
public class ReputationManager {

  private static final String DEFAULT_REPUTATION_FILE = "reputation.dat";
  private static final String BACKUP_SUFFIX = ".bak";
  private static final long DEFAULT_SAVE_INTERVAL_MS = 60_000; // 1 minute
  private static final int DEFAULT_INITIAL_REPUTATION = 100;

  private final Path reputationFile;
  private final Path backupFile;
  private final Map<String, ReputationData> reputations = new ConcurrentHashMap<>();
  private final ScheduledExecutorService saveExecutor;
  private volatile boolean running = false;

  /**
   * Creates a reputation manager with default settings.
   *
   * @param dataDir the directory to store reputation data
   */
  public ReputationManager(String dataDir) {
    this(dataDir, DEFAULT_SAVE_INTERVAL_MS);
  }

  /**
   * Creates a reputation manager with custom save interval.
   *
   * @param dataDir the directory to store reputation data
   * @param saveIntervalMs how often to save reputation data (in milliseconds)
   */
  public ReputationManager(String dataDir, long saveIntervalMs) {
    Path dir = Paths.get(dataDir);
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      log.error("Failed to create reputation data directory: {}", dir, e);
    }

    this.reputationFile = dir.resolve(DEFAULT_REPUTATION_FILE);
    this.backupFile = dir.resolve(DEFAULT_REPUTATION_FILE + BACKUP_SUFFIX);

    this.saveExecutor = Executors.newSingleThreadScheduledExecutor(
        BasicThreadFactory.builder()
            .namingPattern("reputation-save-%d")
            .daemon(true)
            .build());

    // Load existing reputation data
    load();

    // Schedule periodic saves
    this.running = true;
    this.saveExecutor.scheduleWithFixedDelay(
        this::save,
        saveIntervalMs,
        saveIntervalMs,
        TimeUnit.MILLISECONDS);

    log.info("ReputationManager started: file={}, saveInterval={}ms",
             reputationFile, saveIntervalMs);
  }

  /**
   * Gets the reputation score for a node, with decay applied.
   *
   * @param nodeId the node identifier
   * @return the current reputation score (with decay)
   */
  public int getReputation(String nodeId) {
    ReputationData data = reputations.get(nodeId);
    if (data == null) {
      return DEFAULT_INITIAL_REPUTATION;
    }
    return data.getDecayedScore();
  }

  /**
   * Updates the reputation score for a node.
   *
   * @param nodeId the node identifier
   * @param score the new reputation score
   */
  public void setReputation(String nodeId, int score) {
    reputations.put(nodeId, new ReputationData(score, System.currentTimeMillis()));
  }

  /**
   * Loads reputation data from disk.
   */
  public synchronized void load() {
    Path fileToLoad = reputationFile;

    // Try main file first, fall back to backup
    if (!Files.exists(fileToLoad) && Files.exists(backupFile)) {
      log.info("Main reputation file not found, using backup");
      fileToLoad = backupFile;
    }

    if (!Files.exists(fileToLoad)) {
      log.info("No existing reputation data found");
      return;
    }

    try (ObjectInputStream ois = new ObjectInputStream(
        new BufferedInputStream(Files.newInputStream(fileToLoad)))) {

      @SuppressWarnings("unchecked")
      Map<String, ReputationData> loaded = (Map<String, ReputationData>) ois.readObject();
      reputations.clear();
      reputations.putAll(loaded);

      log.info("Loaded {} node reputations from {}", reputations.size(), fileToLoad);

    } catch (IOException | ClassNotFoundException e) {
      log.error("Failed to load reputation data from {}", fileToLoad, e);
    }
  }

  /**
   * Saves reputation data to disk atomically.
   */
  public synchronized void save() {
    if (!running) {
      return;
    }

    if (reputations.isEmpty()) {
      log.debug("No reputation data to save");
      return;
    }

    Path tempFile = null;
    try {
      // Write to temp file first
      tempFile = Files.createTempFile(reputationFile.getParent(), "reputation", ".tmp");

      try (ObjectOutputStream oos = new ObjectOutputStream(
          new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
        oos.writeObject(new ConcurrentHashMap<>(reputations));
        oos.flush();
      }

      // Backup existing file if it exists
      if (Files.exists(reputationFile)) {
        Files.copy(reputationFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
      }

      // Atomically replace with new file
      Files.move(tempFile, reputationFile, StandardCopyOption.REPLACE_EXISTING);

      log.debug("Saved {} node reputations to {}", reputations.size(), reputationFile);

    } catch (IOException e) {
      log.error("Failed to save reputation data to {}", reputationFile, e);

      // Clean up temp file on error
      if (tempFile != null && Files.exists(tempFile)) {
        try {
          Files.delete(tempFile);
        } catch (IOException ex) {
          log.warn("Failed to delete temp file: {}", tempFile, ex);
        }
      }
    }
  }

  /**
   * Stops the reputation manager and performs final save.
   */
  public void stop() {
    log.info("Stopping ReputationManager");
    running = false;

    // Perform final save
    save();

    // Shutdown executor
    saveExecutor.shutdown();
    try {
      if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        saveExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      saveExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Gets the number of nodes with reputation data.
   *
   * @return the count of tracked nodes
   */
  public int size() {
    return reputations.size();
  }

  /**
   * Clears all reputation data (useful for testing).
   */
  public void clear() {
    reputations.clear();
    log.info("Cleared all reputation data");
  }

  /**
   * Internal data structure for storing reputation with timestamp.
   */
  private static class ReputationData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // Decay parameters
    private static final long DECAY_INTERVAL_MS = 86_400_000; // 1 day
    private static final int DECAY_AMOUNT = 5; // Points to decay per day
    private static final int NEUTRAL_SCORE = 100;

    private final int score;
    @Getter
    private final long timestamp;

    public ReputationData(int score, long timestamp) {
      this.score = score;
      this.timestamp = timestamp;
    }

    /**
     * Gets the reputation score with time-based decay applied.
     * Scores decay towards neutral (100) over time.
     *
     * @return the decayed reputation score
     */
    public int getDecayedScore() {
      long ageMs = System.currentTimeMillis() - timestamp;
      long daysSinceUpdate = ageMs / DECAY_INTERVAL_MS;

      if (daysSinceUpdate == 0) {
        return score;
      }

      // Decay towards neutral score
      int totalDecay = (int) (daysSinceUpdate * DECAY_AMOUNT);

      if (score > NEUTRAL_SCORE) {
        // Good reputation decays down towards neutral
        return Math.max(NEUTRAL_SCORE, score - totalDecay);
      } else if (score < NEUTRAL_SCORE) {
        // Bad reputation recovers up towards neutral
        return Math.min(NEUTRAL_SCORE, score + totalDecay);
      }

      return NEUTRAL_SCORE;
    }

  }
}
