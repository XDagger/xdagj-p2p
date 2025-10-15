/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 */
package io.xdag.p2p.performance;

/**
 * Quick runner for performance benchmarks
 * Usage: mvn exec:java -Dexec.mainClass="io.xdag.p2p.performance.QuickBenchmarkRunner"
 */
public class QuickBenchmarkRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("XDAG P2P Performance Benchmark Suite");
        System.out.println("=".repeat(80));
        System.out.println();

        // Codec benchmarks
        MessageCodecBenchmarkTest codecTest = new MessageCodecBenchmarkTest();
        System.out.println("Running codec benchmarks...\n");
        codecTest.testCodecPerformance_WithCompression();
        System.out.println();
        codecTest.testCodecPerformance_WithoutCompression();
        System.out.println();
        codecTest.testCompressionImpact_VariousSizes();
        System.out.println();
        codecTest.testCodecThroughput();
        System.out.println();

        // Message processing benchmarks
        MessageProcessingBenchmarkTest processingTest = new MessageProcessingBenchmarkTest();
        System.out.println("\nRunning message processing benchmarks...\n");
        processingTest.testSingleThreadMessageHandlingThroughput();
        System.out.println();
        processingTest.testMessageHandlingBreakdown();
        System.out.println();
        processingTest.testAsyncForwardingOverhead();
        System.out.println();

        System.out.println("=".repeat(80));
        System.out.println("Benchmark Complete!");
        System.out.println("=".repeat(80));
    }
}
