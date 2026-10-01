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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Remembers, across restarts, how nodes behaved in discovery: a score per node id that decays towards neutral.
 *
 * <p>The store is bounded ({@link #MAX_ENTRIES}; the least recently updated entries go first) because node ids
 * are cheap to make up, and it is a plain text file (one {@code id score timestamp} line per node) rather than
 * Java serialisation, so loading it cannot instantiate anything.
 */
@Slf4j
public class ReputationManager {

  private static final String DEFAULT_REPUTATION_FILE = "reputation.dat";
  private static final String BACKUP_SUFFIX = ".bak";
  private static final long DEFAULT_SAVE_INTERVAL_MS = 60_000; // 1 minute
  private static final int DEFAULT_INITIAL_REPUTATION = 100;
  private static final int MIN_SCORE = 0;
  private static final int MAX_SCORE = 200;
  /** Most node ids remembered. */
  public static final int MAX_ENTRIES = 20_000;
  private static final String FILE_HEADER = "# xdagj-p2p reputation v1";

  private final Path reputationFile;
  private final Path backupFile;
  private final Map<String, ReputationData> reputations = new ConcurrentHashMap<>();
  private final ScheduledExecutorService saveExecutor;
  private volatile boolean running = false;

  public ReputationManager(String dataDir) {
    this(dataDir, DEFAULT_SAVE_INTERVAL_MS);
  }

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

    log.debug("ReputationManager started: file={}, saveInterval={}ms",
             reputationFile, saveIntervalMs);
  }

  public int getReputation(String nodeId) {
    ReputationData data = nodeId == null ? null : reputations.get(nodeId);
    if (data == null) {
      return DEFAULT_INITIAL_REPUTATION;
    }
    return data.getDecayedScore();
  }

  public void setReputation(String nodeId, int score) {
    if (nodeId == null) {
      return;
    }
    reputations.put(nodeId, new ReputationData(clamp(score), System.currentTimeMillis()));
    if (reputations.size() > MAX_ENTRIES) {
      evictOldest();
    }
  }

  private static int clamp(int score) {
    return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
  }

  private synchronized void evictOldest() {
    int excess = reputations.size() - MAX_ENTRIES;
    if (excess <= 0) {
      return;
    }
    List<Map.Entry<String, ReputationData>> entries = new ArrayList<>(reputations.entrySet());
    entries.sort(Comparator.comparingLong(e -> e.getValue().getTimestamp()));
    for (int i = 0; i < excess + MAX_ENTRIES / 100 && i < entries.size(); i++) {
      reputations.remove(entries.get(i).getKey(), entries.get(i).getValue());
    }
  }

  public synchronized void load() {
    Path fileToLoad = reputationFile;

    // Try main file first, fall back to backup
    if (!Files.exists(fileToLoad) && Files.exists(backupFile)) {
      log.info("Main reputation file not found, using backup");
      fileToLoad = backupFile;
    }

    if (!Files.exists(fileToLoad)) {
      log.debug("No existing reputation data found");
      return;
    }

    Map<String, ReputationData> loaded = new ConcurrentHashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null && loaded.size() < MAX_ENTRIES) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 3) {
          continue;
        }
        try {
          loaded.put(parts[0], new ReputationData(clamp(Integer.parseInt(parts[1])), Long.parseLong(parts[2])));
        } catch (NumberFormatException e) {
          // a damaged line: skipped
        }
      }
      reputations.clear();
      reputations.putAll(loaded);
      log.debug("Loaded {} node reputations from {}", reputations.size(), fileToLoad);
    } catch (IOException e) {
      log.error("Failed to load reputation data from {}", fileToLoad, e);
    }
  }

  public synchronized void save() {
    if (!running) {
      return;
    }

    if (reputations.isEmpty()) {
      log.trace("No reputation data to save");
      return;
    }

    Path tempFile = null;
    try {
      // Write to temp file first
      tempFile = Files.createTempFile(reputationFile.getParent(), "reputation", ".tmp");

      try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
        writer.write(FILE_HEADER);
        writer.newLine();
        for (Map.Entry<String, ReputationData> entry : reputations.entrySet()) {
          String id = entry.getKey();
          if (id.isEmpty() || id.chars().anyMatch(Character::isWhitespace)) {
            continue;
          }
          writer.write(id + " " + entry.getValue().score + " " + entry.getValue().timestamp);
          writer.newLine();
        }
      }

      // Backup existing file if it exists
      if (Files.exists(reputationFile)) {
        Files.copy(reputationFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
      }

      // Atomically replace with new file
      Files.move(tempFile, reputationFile, StandardCopyOption.REPLACE_EXISTING);

      log.trace("Saved {} node reputations to {}", reputations.size(), reputationFile);

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

  public void stop() {
    log.debug("Stopping ReputationManager");
    running = false;

    // Perform final save
    running = true;
    save();
    running = false;

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

  public int size() {
    return reputations.size();
  }

  public void clear() {
    reputations.clear();
    log.debug("Cleared all reputation data");
  }

  private static class ReputationData {

    // Decay parameters
    private static final long DECAY_INTERVAL_MS = 86_400_000; // 1 day
    private static final int DECAY_AMOUNT = 5; // Points to decay per day
    private static final int NEUTRAL_SCORE = 100;

    private final int score;
    @Getter
    private final long timestamp;

    ReputationData(int score, long timestamp) {
      this.score = score;
      this.timestamp = timestamp;
    }

    int getDecayedScore() {
      long ageMs = System.currentTimeMillis() - timestamp;
      long daysSinceUpdate = ageMs / DECAY_INTERVAL_MS;

      if (daysSinceUpdate <= 0) {
        return score;
      }

      // Decay towards neutral score
      int totalDecay = (int) Math.min(Integer.MAX_VALUE, daysSinceUpdate * DECAY_AMOUNT);

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
