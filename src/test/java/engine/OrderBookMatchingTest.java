package engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookMatchingTest {

    @Test
    void matchesCrossingOrders() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 5));

        var events = book.submit(new NewOrder(2, Side.BUY, 105, 5));

        assertEquals(List.of(Event.trade(2, 1, 100, 5)), events); // resting price, not 105
        assertTrue(book.bestAsk().isEmpty());
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void preservesPricePriority() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 101, 2)); // arrives first, worse price
        book.submit(new NewOrder(2, Side.SELL, 100, 2)); // arrives later, better price

        var events = book.submit(new NewOrder(3, Side.BUY, 101, 2));

        assertEquals(List.of(Event.trade(3, 2, 100, 2)), events);
        assertEquals(101L, book.bestAsk().orElseThrow()); // order 1 untouched
    }

    @Test
    void preservesFifoWithinPriceLevel() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 3));
        book.submit(new NewOrder(2, Side.SELL, 100, 3));

        var events = book.submit(new NewOrder(3, Side.BUY, 100, 3));

        assertEquals(List.of(Event.trade(3, 1, 100, 3)), events);
    }

    @Test
    void largeOrderSweepsLevelsAndRestsRemainder() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 2));
        book.submit(new NewOrder(2, Side.SELL, 101, 2));

        var events = book.submit(new NewOrder(3, Side.BUY, 101, 5));

        assertEquals(List.of(
                Event.trade(3, 1, 100, 2),
                Event.trade(3, 2, 101, 2),
                Event.accepted(3, 101, 1)), events);
        assertEquals(101L, book.bestBid().orElseThrow());
    }

    @Test
    void partialFillLeavesRestingOrderInBook() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 10));
        book.submit(new NewOrder(2, Side.BUY, 100, 4));

        assertEquals(100L, book.bestAsk().orElseThrow());

        var events = book.submit(new NewOrder(3, Side.BUY, 100, 6));

        assertEquals(List.of(Event.trade(3, 1, 100, 6)), events);
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void nonCrossingOrderRests() {
        var book = new OrderBook();

        var events = book.submit(new NewOrder(1, Side.BUY, 99, 5));

        assertEquals(List.of(Event.accepted(1, 99, 5)), events);
        assertEquals(99L, book.bestBid().orElseThrow());
    }

    @Test
    void rejectsBadQuantityAndDuplicateId() {
        var book = new OrderBook();

        assertEquals(List.of(Event.rejected(1)), book.submit(new NewOrder(1, Side.BUY, 100, 0)));

        book.submit(new NewOrder(2, Side.BUY, 100, 5));
        assertEquals(List.of(Event.rejected(2)), book.submit(new NewOrder(2, Side.BUY, 100, 5)));
    }
}