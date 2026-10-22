package engine.codec;

import engine.Event;
import engine.NewOrder;
import engine.OrderBook;
import engine.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.util.List;

import engine.codec.ExecType;
import engine.codec.OrderSide;

/**
 * Decodes SBE input messages, runs them through the order book, and encodes
 * the resulting events as ExecutionReport messages.
 */
public final class EngineAdapter {

    private final OrderBook book;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final NewOrderDecoder newOrderDecoder = new NewOrderDecoder();
    private final CancelOrderDecoder cancelOrderDecoder = new CancelOrderDecoder();
    private final ExecutionReportEncoder reportEncoder = new ExecutionReportEncoder();

    public EngineAdapter(OrderBook book) {
        this.book = book;
    }

    /**
     * Handles one input message.
     *
     * @return number of bytes written to {@code out}: zero or more concatenated
     *         ExecutionReport messages, each with its own header
     */
    public int onMessage(DirectBuffer in, int inOffset, MutableDirectBuffer out, int outOffset) {
        headerDecoder.wrap(in, inOffset);
        final int bodyOffset = inOffset + headerDecoder.encodedLength();
        final int templateId = headerDecoder.templateId();

        final List<Event> events = switch (templateId) {
            case NewOrderDecoder.TEMPLATE_ID -> submit(in, bodyOffset);
            case CancelOrderDecoder.TEMPLATE_ID -> cancel(in, bodyOffset);
            default -> throw new IllegalArgumentException("Unknown template id " + templateId);
        };

        return writeReports(events, out, outOffset);
    }

    private List<Event> submit(DirectBuffer in, int bodyOffset) {
        newOrderDecoder.wrap(in, bodyOffset, headerDecoder.blockLength(), headerDecoder.version());
        return book.submit(new NewOrder(
                newOrderDecoder.orderId(),
                toSide(newOrderDecoder.side()),
                newOrderDecoder.price(),
                newOrderDecoder.quantity()));
    }

    private List<Event> cancel(DirectBuffer in, int bodyOffset) {
        cancelOrderDecoder.wrap(in, bodyOffset, headerDecoder.blockLength(), headerDecoder.version());
        return book.cancel(cancelOrderDecoder.orderId());
    }

    private int writeReports(List<Event> events, MutableDirectBuffer out, int outOffset) {
        int pos = outOffset;
        for (Event e : events) {
            reportEncoder.wrapAndApplyHeader(out, pos, headerEncoder)
                    .type(toExecType(e.type()))
                    .orderId(e.orderID())
                    .counterpartyId(e.counterpartyId())
                    .price(e.price())
                    .quantity(e.quantity());
            pos += MessageHeaderEncoder.ENCODED_LENGTH + reportEncoder.encodedLength();
        }
        return pos - outOffset;
    }

    private static Side toSide(OrderSide side) {
        return switch (side) {
            case BUY -> Side.BUY;
            case SELL -> Side.SELL;
            default -> throw new IllegalArgumentException("Unknown side " + side);
        };
    }

    private static ExecType toExecType(Event.Type type) {
        return switch (type) {
            case ACCEPTED -> ExecType.ACCEPTED;
            case TRADE -> ExecType.TRADE;
            case CANCELLED -> ExecType.CANCELLED;
            case REJECTED -> ExecType.REJECTED;
        };
    }
}