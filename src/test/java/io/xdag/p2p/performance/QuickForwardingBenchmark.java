package io.xdag.p2p.performance;

public class QuickForwardingBenchmark {
    public static void main(String[] args) throws Exception {
        ForwardingBottleneckTest test = new ForwardingBottleneckTest();
        test.testForwardingOperationsBreakdown();
    }
}
