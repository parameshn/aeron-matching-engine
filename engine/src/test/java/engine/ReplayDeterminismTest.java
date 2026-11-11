package engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ReplayDeterminismTest {

    private record Op(boolean cancel, long id, Side side, long price, long quantity) {
    }

    private static List<Op> generate(long seed, int count) {
        final Random random = new Random(seed);
        final List<Op> ops = new ArrayList<>(count);
        long nextId = 1;
        for (int i = 0; i < count; i++) {
            if (nextId > 1 && random.nextInt(5) == 0) {
                // may target an order that has already filled: that exercises REJECTED too
                ops.add(new Op(true, 1 + random.nextInt((int) nextId - 1), null, 0, 0));
            } else {
                final Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
                ops.add(new Op(false, nextId++, side, 95 + random.nextInt(11), 1 + random.nextInt(10)));
            }
        }
        return ops;
    }

    private static List<Event> apply(OrderBook book, Op op) {
        return op.cancel()
                ? book.cancel(op.id())
                : book.submit(new NewOrder(op.id(), op.side(), op.price(), op.quantity()));
    }

    @Test
    void sameOperationsGiveSameEventsAndStateAtEveryStep() {
        for (long seed = 1; seed <= 20; seed++) {
            final OrderBook a = new OrderBook();
            final OrderBook b = new OrderBook();
            for (Op op : generate(seed, 3_000)) {
                assertEquals(apply(a, op), apply(b, op), "seed " + seed);
                assertEquals(a.stateHash(), b.stateHash(), "seed " + seed);
            }
        }
    }

    @Test
    void bookRestoredFromSnapshotBehavesLikeTheOriginal() {
        for (long seed = 1; seed <= 20; seed++) {
            final List<Op> ops = generate(seed, 3_000);
            final OrderBook live = new OrderBook();
            for (Op op : ops.subList(0, 1_500)) {
                apply(live, op);
            }

            final OrderBook restored = new OrderBook();
            live.forEachResting(restored::restore);
            assertEquals(live.restingOrders(), restored.restingOrders(), "seed " + seed);
            assertEquals(live.stateHash(), restored.stateHash(), "seed " + seed);

            for (Op op : ops.subList(1_500, ops.size())) {
                assertEquals(apply(live, op), apply(restored, op), "seed " + seed);
                assertEquals(live.stateHash(), restored.stateHash(), "seed " + seed);
            }
        }
    }
}