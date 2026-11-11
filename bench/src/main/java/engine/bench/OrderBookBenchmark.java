package engine.bench;

import engine.NewOrder;
import engine.OrderBook;
import engine.Side;
import org.HdrHistogram.Histogram;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Random;

/**
 * Baseline for OrderBook.submit: latency percentiles, throughput, allocation and GC.
 * In-process only (no network). Orders are generated before timing starts.
 */
public final class OrderBookBenchmark {

    private static final int OPS_PER_ROUND = 1_000_000;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 5;

    public static void main(String[] args) {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final Histogram histogram = new Histogram(60_000_000_000L, 3);

        // Warmup lets the JIT compile the hot path. Nothing is recorded.
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            final NewOrder[] ops = generate(round);
            final OrderBook book = new OrderBook();
            for (NewOrder op : ops) {
                book.submit(op);
            }
        }

        final long gcCountBefore = gcCount();
        final long gcMillisBefore = gcMillis();
        long allocatedBytes = 0;
        long elapsedNanos = 0;

        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            final NewOrder[] ops = generate(1_000 + round);
            final OrderBook book = new OrderBook();

            final long allocBefore = threads.getCurrentThreadAllocatedBytes();
            final long start = System.nanoTime();
            for (NewOrder op : ops) {
                final long t0 = System.nanoTime();
                book.submit(op);
                histogram.recordValue(System.nanoTime() - t0);
            }
            elapsedNanos += System.nanoTime() - start;
            allocatedBytes += threads.getCurrentThreadAllocatedBytes() - allocBefore;
        }

        final long totalOps = (long) OPS_PER_ROUND * MEASURED_ROUNDS;
        System.out.printf("operations          : %,d%n", totalOps);
        System.out.printf("throughput (approx) : %,.0f ops/s (includes timer overhead)%n",
                totalOps / (elapsedNanos / 1e9));
        System.out.printf("latency p50         : %,d ns%n", histogram.getValueAtPercentile(50.0));
        System.out.printf("latency p99         : %,d ns%n", histogram.getValueAtPercentile(99.0));
        System.out.printf("latency p99.9       : %,d ns%n", histogram.getValueAtPercentile(99.9));
        System.out.printf("latency p99.99      : %,d ns%n", histogram.getValueAtPercentile(99.99));
        System.out.printf("latency max         : %,d ns%n", histogram.getMaxValue());
        System.out.printf("allocated per op    : %,d bytes%n", allocatedBytes / totalOps);
        System.out.printf("GC collections      : %d (%d ms)%n",
                gcCount() - gcCountBefore, gcMillis() - gcMillisBefore);
    }

    private static NewOrder[] generate(long seed) {
        final Random random = new Random(seed);
        final NewOrder[] ops = new NewOrder[OPS_PER_ROUND];
        for (int i = 0; i < ops.length; i++) {
            final Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            final long price = 990 + random.nextInt(21);   // 990..1010, so many orders cross
            final long quantity = 1 + random.nextInt(10);
            ops[i] = new NewOrder(i + 1, side, price, quantity);
        }
        return ops;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
}