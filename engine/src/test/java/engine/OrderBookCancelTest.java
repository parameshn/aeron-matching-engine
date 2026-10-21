package engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookCancelTest {

    @Test
    void cancelsAnOpenOrder() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.BUY, 100, 5));

        assertEquals(List.of(Event.cancelled(1)), book.cancel(1));
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void cannotCancelUnknownOrder() {
        var book = new OrderBook();

        assertEquals(List.of(Event.rejected(99)), book.cancel(99));
    }

    @Test
    void cannotCancelFullyFilledOrder() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 5));
        book.submit(new NewOrder(2, Side.BUY, 100, 5)); // fills order 1 completely

        assertEquals(List.of(Event.rejected(1)), book.cancel(1));
    }

    @Test
    void cannotCancelTwice() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.BUY, 100, 5));

        assertEquals(List.of(Event.cancelled(1)), book.cancel(1));
        assertEquals(List.of(Event.rejected(1)), book.cancel(1));
    }
}