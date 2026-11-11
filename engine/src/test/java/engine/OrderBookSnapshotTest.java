package engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookSnapshotTest {

    private record Resting(long id, Side side, long price, long remaining) {
    }

    private static List<Resting> contents(OrderBook book) {
        List<Resting> out = new ArrayList<>();
        book.forEachResting((id, side, price, rem) -> out.add(new Resting(id, side, price, rem)));
        return out;
    }

    private static OrderBook copyOf(OrderBook source) {
        OrderBook copy = new OrderBook();
        source.forEachResting(copy::restore);
        return copy;
    }

    @Test
    void emptyBookRoundTrips() {
        var copy = copyOf(new OrderBook());

        assertEquals(0, copy.restingOrders());
        assertEquals(new OrderBook().stateHash(), copy.stateHash());
    }

    @Test
    void visitsBidsBestFirstThenAsksBestFirstOldestFirstWithinPrice() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.BUY, 99, 5));
        book.submit(new NewOrder(2, Side.BUY, 100, 5));
        book.submit(new NewOrder(3, Side.BUY, 100, 7));
        book.submit(new NewOrder(4, Side.SELL, 103, 2));
        book.submit(new NewOrder(5, Side.SELL, 102, 3));

        assertEquals(List.of(
                new Resting(2, Side.BUY, 100, 5),
                new Resting(3, Side.BUY, 100, 7),
                new Resting(1, Side.BUY, 99, 5),
                new Resting(5, Side.SELL, 102, 3),
                new Resting(4, Side.SELL, 103, 2)), contents(book));
    }

    @Test
    void restoredBookHasSameContentsAndHash() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.BUY, 99, 5));
        book.submit(new NewOrder(2, Side.SELL, 103, 2));
        book.submit(new NewOrder(3, Side.BUY, 100, 7));

        var copy = copyOf(book);

        assertEquals(contents(book), contents(copy));
        assertEquals(book.stateHash(), copy.stateHash());
        assertEquals(book.restingOrders(), copy.restingOrders());
    }

    @Test
    void snapshotCarriesRemainingQuantityAfterPartialFill() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 10));
        book.submit(new NewOrder(2, Side.BUY, 100, 4)); // order 1 has 6 left

        var copy = copyOf(book);

        assertEquals(List.of(new Resting(1, Side.SELL, 100, 6)), contents(copy));
        assertEquals(List.of(Event.trade(3, 1, 100, 6)),
                copy.submit(new NewOrder(3, Side.BUY, 100, 6)));
        assertEquals(0, copy.restingOrders());
    }

    @Test
    void restoredBookKeepsFifoWithinPrice() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 3));
        book.submit(new NewOrder(2, Side.SELL, 100, 3));

        var copy = copyOf(book);

        assertEquals(List.of(Event.trade(3, 1, 100, 3)),
                copy.submit(new NewOrder(3, Side.BUY, 100, 3)));
    }

    @Test
    void differentStatesGiveDifferentHashes() {
        var a = new OrderBook();
        var b = new OrderBook();
        a.submit(new NewOrder(1, Side.BUY, 100, 5));
        b.submit(new NewOrder(1, Side.BUY, 100, 6));

        assertNotEquals(a.stateHash(), b.stateHash());
    }

    @Test
    void restoreRejectsNonPositiveRemaining() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBook().restore(1, Side.BUY, 100, 0));
    }

    @Test
    void restoreRejectsDuplicateId() {
        var book = new OrderBook();
        book.restore(1, Side.BUY, 100, 5);

        assertThrows(IllegalArgumentException.class, () -> book.restore(1, Side.SELL, 101, 5));
    }
}