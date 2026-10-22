package engine.codec;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import engine.codec.ExecutionReportDecoder;

import static org.junit.jupiter.api.Assertions.*;

class CodecRoundTripTest {

    private final UnsafeBuffer buf = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    @Test
    void newOrderRoundTrips() {
        new NewOrderEncoder()
                .wrapAndApplyHeader(buf, 0, new MessageHeaderEncoder())
                .orderId(7).side(OrderSide.SELL).price(100).quantity(5);

        var header = new MessageHeaderDecoder().wrap(buf, 0);
        assertEquals(NewOrderDecoder.TEMPLATE_ID, header.templateId());

        var d = new NewOrderDecoder()
                .wrap(buf, header.encodedLength(), header.blockLength(), header.version());
        assertEquals(7L, d.orderId());
        assertEquals(OrderSide.SELL, d.side());
        assertEquals(100L, d.price());
        assertEquals(5L, d.quantity());
    }

    @Test
    void executionReportRoundTrips() {
        new ExecutionReportEncoder()
                .wrapAndApplyHeader(buf, 0, new MessageHeaderEncoder())
                .type(ExecType.TRADE).orderId(2).counterpartyId(1).price(100).quantity(5);

        var header = new MessageHeaderDecoder().wrap(buf, 0);
        assertEquals(ExecutionReportDecoder.TEMPLATE_ID, header.templateId());

        var d = new ExecutionReportDecoder()
                .wrap(buf, header.encodedLength(), header.blockLength(), header.version());
        assertEquals(ExecType.TRADE, d.type());
        assertEquals(2L, d.orderId());
        assertEquals(1L, d.counterpartyId());
        assertEquals(100L, d.price());
        assertEquals(5L, d.quantity());
    }
}