package engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookDeterminismTest {

    private static final List<NewOrder> SEQUENCE = List.of(
            new NewOrder(1, Side.SELL, 100, 3),
            new NewOrder(2, Side.SELL, 100, 3),
            new NewOrder(3, Side.SELL, 101, 4),
            new NewOrder(4, Side.BUY, 99, 5),
            new NewOrder(5, Side.BUY, 101, 8), // takes 1 and 2 fully, part of 3
            new NewOrder(6, Side.BUY, 100, 2) // does not cross, rests
    );

    private static List<Event> run(OrderBook book) {
        List<Event> all = new ArrayList<>();
        for (NewOrder o : SEQUENCE)
            all.addAll(book.submit(o));
        return all;
    }

    @Test
    void sameInputSequenceProducesSameEvents() {
        var a = new OrderBook();
        var b = new OrderBook();

        assertEquals(run(a), run(b));
        assertEquals(a.bestBid(), b.bestBid());
        assertEquals(a.bestAsk(), b.bestAsk());
    }

    @Test
    void eventOrderingIsStable() {
        var events = run(new OrderBook());

        assertEquals(List.of(
                Event.accepted(1, 100, 3),
                Event.accepted(2, 100, 3),
                Event.accepted(3, 101, 4),
                Event.accepted(4, 99, 5),
                Event.trade(5, 1, 100, 3),
                Event.trade(5, 2, 100, 3),
                Event.trade(5, 3, 101, 2),
                Event.accepted(6, 100, 2)), events);
    }
}