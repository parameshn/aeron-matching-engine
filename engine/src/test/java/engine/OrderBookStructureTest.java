package engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookStructureTest {

    @Test
    void emptyBookHasNoBestPrices() {
        var book = new OrderBook();

        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void bestBidIsHighestPrice() {
        var book = new OrderBook();
        book.rest(new Order(1, Side.BUY, 99, 5));
        book.rest(new Order(2, Side.BUY, 101, 5));
        book.rest(new Order(3, Side.BUY, 100, 5));

        assertEquals(101L, book.bestBid().orElseThrow());
    }

    @Test
    void bestAskIsLowestPrice() {
        var book = new OrderBook();
        book.rest(new Order(1, Side.SELL, 105, 5));
        book.rest(new Order(2, Side.SELL, 103, 5));

        assertEquals(103L, book.bestAsk().orElseThrow());
    }

    @Test
    void cancelRemovesOrderAndEmptyLevel() {
        var book = new OrderBook();
        book.rest(new Order(1, Side.BUY, 100, 5));

        assertEquals(List.of(Event.cancelled(1)), book.cancel(1));
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void cancelKeepsLevelWhenOtherOrdersRemain() {
        var book = new OrderBook();
        book.rest(new Order(1, Side.BUY, 100, 5));
        book.rest(new Order(2, Side.BUY, 100, 5));

        assertEquals(List.of(Event.cancelled(1)), book.cancel(1));
        assertEquals(100L, book.bestBid().orElseThrow());
    }

    @Test
    void duplicateIdThrowsWhenResting() {
        var book = new OrderBook();
        book.rest(new Order(1, Side.BUY, 100, 5));

        assertThrows(IllegalArgumentException.class,
                () -> book.rest(new Order(1, Side.BUY, 101, 5)));
    }
}