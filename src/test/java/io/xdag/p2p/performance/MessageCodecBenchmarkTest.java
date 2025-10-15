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
package io.xdag.p2p.performance;

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.p2p.channel.XdagFrame;
import io.xdag.p2p.channel.XdagMessageHandler;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.example.message.AppTestMessage;
import io.xdag.p2p.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Micro-benchmark test for message encoding/decoding performance
 * Quickly identifies codec bottlenecks without full network setup
 */
public class MessageCodecBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;

    /**
     * Benchmark: Encode/Decode with compression enabled
     * Expected: ~10-20μs per message for small messages
     */
    @Test
    public void testCodecPerformance_WithCompression() throws Exception {
        P2pConfig config = createConfig(true);
        BenchmarkResult result = runCodecBenchmark(config, 256);

        System.out.println("=== WITH Compression (256 bytes) ===");
        printResult(result);

        // Assert reasonable performance
        assertTrue(result.avgLatencyUs < 100, "Encode/Decode should be under 100μs with compression");
    }

    /**
     * Benchmark: Encode/Decode without compression
     * Expected: ~5-10μs per message for small messages
     */
    @Test
    public void testCodecPerformance_WithoutCompression() throws Exception {
        P2pConfig config = createConfig(false);
        BenchmarkResult result = runCodecBenchmark(config, 256);

        System.out.println("=== WITHOUT Compression (256 bytes) ===");
        printResult(result);

        // Assert reasonable performance
        assertTrue(result.avgLatencyUs < 50, "Encode/Decode should be under 50μs without compression");
    }

    /**
     * Compare compression impact across different message sizes
     */
    @Test
    public void testCompressionImpact_VariousSizes() throws Exception {
        int[] sizes = {64, 256, 1024, 4096};

        System.out.println("\n=== Compression Impact Analysis ===");
        System.out.printf("%-10s | %-15s | %-15s | %-10s%n", "Size", "With Compress", "No Compress", "Overhead");
        System.out.println("-".repeat(60));

        for (int size : sizes) {
            BenchmarkResult withCompression = runCodecBenchmark(createConfig(true), size);
            BenchmarkResult noCompression = runCodecBenchmark(createConfig(false), size);

            double overhead = ((withCompression.avgLatencyUs - noCompression.avgLatencyUs)
                               / noCompression.avgLatencyUs) * 100;

            System.out.printf("%-10d | %-15.2f | %-15.2f | +%.1f%%%n",
                    size,
                    withCompression.avgLatencyUs,
                    noCompression.avgLatencyUs,
                    overhead);
        }
    }

    /**
     * Benchmark: Measure throughput (messages per second)
     */
    @Test
    public void testCodecThroughput() throws Exception {
        P2pConfig config = createConfig(false);
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        byte[] payload = createTestPayload(256);
        Message message = new AppTestMessage(payload);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            channel.writeOutbound(message);
            channel.readOutbound();
            channel.flushOutbound();
        }

        // Benchmark throughput
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            channel.writeOutbound(message);
            channel.readOutbound();
            channel.flushOutbound();
        }
        long endTime = System.nanoTime();

        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = BENCHMARK_ITERATIONS / durationSec;

        System.out.println("\n=== Throughput Test (256 bytes, no compression) ===");
        System.out.printf("Messages: %d%n", BENCHMARK_ITERATIONS);
        System.out.printf("Duration: %.3f sec%n", durationSec);
        System.out.printf("Throughput: %.0f msg/s%n", throughput);

        // Target: at least 100K msg/s for codec layer alone
        assertTrue(throughput > 50000,
                String.format("Codec throughput too low: %.0f msg/s (expected > 50K)", throughput));

        channel.close();
    }

    /**
     * Benchmark: Memory allocation overhead
     */
    @Test
    public void testMemoryAllocationOverhead() throws Exception {
        P2pConfig config = createConfig(false);

        System.out.println("\n=== Memory Allocation Analysis ===");

        for (int size : new int[]{256, 1024, 4096}) {
            Runtime runtime = Runtime.getRuntime();

            // Force GC before test
            System.gc();
            Thread.sleep(100);

            long memBefore = runtime.totalMemory() - runtime.freeMemory();

            runCodecBenchmark(config, size);

            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsed = memAfter - memBefore;
            double memPerMessage = memUsed / (double) BENCHMARK_ITERATIONS;

            System.out.printf("Size: %4d bytes | Memory/msg: %.2f bytes%n",
                    size, memPerMessage);
        }
    }

    // Helper methods

    private P2pConfig createConfig(boolean enableCompression) {
        P2pConfig config = new P2pConfig();
        config.setEnableFrameCompression(enableCompression);
        return config;
    }

    private BenchmarkResult runCodecBenchmark(P2pConfig config, int payloadSize) throws Exception {
        XdagMessageHandler handler = new XdagMessageHandler(config);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        byte[] payload = createTestPayload(payloadSize);
        Message message = new AppTestMessage(payload);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            channel.writeOutbound(message);
            XdagFrame frame = channel.readOutbound();
            channel.writeInbound(frame);
            Message decoded = channel.readInbound();
            assertNotNull(decoded);
        }

        // Benchmark
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();

            // Encode
            channel.writeOutbound(message);
            XdagFrame frame = channel.readOutbound();

            // Decode
            channel.writeInbound(frame);
            Message decoded = channel.readInbound();

            long end = System.nanoTime();

            assertNotNull(decoded);
            latencies.add(end - start);
        }

        channel.close();

        return analyzeLatencies(latencies);
    }

    private byte[] createTestPayload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i % 256);
        }
        return payload;
    }

    private BenchmarkResult analyzeLatencies(List<Long> latencies) {
        latencies.sort(Long::compareTo);

        long sum = latencies.stream().mapToLong(Long::longValue).sum();
        double avgNs = sum / (double) latencies.size();
        double avgUs = avgNs / 1000.0;

        long p50Ns = latencies.get(latencies.size() / 2);
        long p95Ns = latencies.get((int) (latencies.size() * 0.95));
        long p99Ns = latencies.get((int) (latencies.size() * 0.99));

        double p50Us = p50Ns / 1000.0;
        double p95Us = p95Ns / 1000.0;
        double p99Us = p99Ns / 1000.0;

        return new BenchmarkResult(avgUs, p50Us, p95Us, p99Us);
    }

    private void printResult(BenchmarkResult result) {
        System.out.printf("Iterations: %d%n", BENCHMARK_ITERATIONS);
        System.out.printf("Average:    %.2f μs%n", result.avgLatencyUs);
        System.out.printf("P50:        %.2f μs%n", result.p50LatencyUs);
        System.out.printf("P95:        %.2f μs%n", result.p95LatencyUs);
        System.out.printf("P99:        %.2f μs%n", result.p99LatencyUs);
    }

    private static class BenchmarkResult {
        final double avgLatencyUs;
        final double p50LatencyUs;
        final double p95LatencyUs;
        final double p99LatencyUs;

        BenchmarkResult(double avgLatencyUs, double p50LatencyUs, double p95LatencyUs, double p99LatencyUs) {
            this.avgLatencyUs = avgLatencyUs;
            this.p50LatencyUs = p50LatencyUs;
            this.p95LatencyUs = p95LatencyUs;
            this.p99LatencyUs = p99LatencyUs;
        }
    }
}
