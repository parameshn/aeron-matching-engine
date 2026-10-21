package engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void tradeCarriesBothOrderIds() {
        var e = Event.trade(2, 1, 100, 5);

        assertEquals(Event.Type.TRADE, e.type());
        assertEquals(2L, e.orderID());
        assertEquals(1L, e.counterpartyId());
        assertEquals(100L, e.price());
        assertEquals(5L, e.quantity());
    }

    @Test
    void acceptedHasNoCounterparty() {
        var e = Event.accepted(3, 99, 4);

        assertEquals(Event.Type.ACCEPTED, e.type());
        assertEquals(0L, e.counterpartyId());
        assertEquals(99L, e.price());
        assertEquals(4L, e.quantity());
    }

    @Test
    void cancelledAndRejectedCarryOnlyTheOrderId() {
        assertEquals(new Event(Event.Type.CANCELLED, 5, 0, 0, 0), Event.cancelled(5));
        assertEquals(new Event(Event.Type.REJECTED, 6, 0, 0, 0), Event.rejected(6));
    }

    @Test
    void equalEventsCompareEqual() {
        assertEquals(Event.trade(2, 1, 100, 5), Event.trade(2, 1, 100, 5));
        assertNotEquals(Event.trade(2, 1, 100, 5), Event.trade(2, 1, 100, 4));
    }
}