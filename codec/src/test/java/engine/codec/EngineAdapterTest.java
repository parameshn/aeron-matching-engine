package engine.codec;

import engine.Event;
import engine.OrderBook;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineAdapterTest {

    private final EngineAdapter adapter = new EngineAdapter(new OrderBook());
    private final UnsafeBuffer in = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private final UnsafeBuffer out = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    private List<Event> sendOrder(long id, OrderSide side, long price, long qty) {
        new NewOrderEncoder()
                .wrapAndApplyHeader(in, 0, new MessageHeaderEncoder())
                .orderId(id).side(side).price(price).quantity(qty);
        return decodeReports(out, adapter.onMessage(in, 0, out, 0));
    }

    private List<Event> sendCancel(long id) {
        new CancelOrderEncoder()
                .wrapAndApplyHeader(in, 0, new MessageHeaderEncoder())
                .orderId(id);
        return decodeReports(out, adapter.onMessage(in, 0, out, 0));
    }

    private static List<Event> decodeReports(DirectBuffer buf, int length) {
        var header = new MessageHeaderDecoder();
        var report = new ExecutionReportDecoder();
        List<Event> events = new ArrayList<>();
        int pos = 0;
        while (pos < length) {
            header.wrap(buf, pos);
            assertEquals(ExecutionReportDecoder.TEMPLATE_ID, header.templateId());
            report.wrap(buf, pos + header.encodedLength(), header.blockLength(), header.version());
            events.add(new Event(
                    Event.Type.valueOf(report.type().name()),
                    report.orderId(), report.counterpartyId(), report.price(), report.quantity()));
            pos += header.encodedLength() + header.blockLength();
        }
        return events;
    }

    @Test
    void restingOrderProducesAcceptedReport() {
        assertEquals(List.of(new Event(Event.Type.ACCEPTED, 1, 0, 100, 3)),
                sendOrder(1, OrderSide.SELL, 100, 3));
    }

    @Test
    void crossingOrderProducesTradeReport() {
        sendOrder(1, OrderSide.SELL, 100, 3);

        assertEquals(List.of(new Event(Event.Type.TRADE, 2, 1, 100, 3)),
                sendOrder(2, OrderSide.BUY, 105, 3));
    }

    @Test
    void sweepProducesSeveralReportsInOrder() {
        sendOrder(1, OrderSide.SELL, 100, 2);
        sendOrder(2, OrderSide.SELL, 101, 2);

        assertEquals(List.of(
                new Event(Event.Type.TRADE, 3, 1, 100, 2),
                new Event(Event.Type.TRADE, 3, 2, 101, 2),
                new Event(Event.Type.ACCEPTED, 3, 0, 101, 1)),
                sendOrder(3, OrderSide.BUY, 101, 5));
    }

    @Test
    void cancelProducesCancelledThenRejected() {
        sendOrder(1, OrderSide.BUY, 99, 5);

        assertEquals(List.of(new Event(Event.Type.CANCELLED, 1, 0, 0, 0)), sendCancel(1));
        assertEquals(List.of(new Event(Event.Type.REJECTED, 1, 0, 0, 0)), sendCancel(1));
    }

    @Test
    void unknownTemplateIsRejected() {
        in.putShort(2, (short) 99); // templateId sits at byte offset 2 of the header
        assertThrows(IllegalArgumentException.class, () -> adapter.onMessage(in, 0, out, 0));
    }
}