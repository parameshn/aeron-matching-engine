package engine.codec;

import engine.OrderBook;
import engine.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Writes an order book to SBE snapshot messages and loads it back. */
public final class BookSnapshot {

    /**
     * Receives each encoded message. The buffer is reused, so consume it before
     * returning.
     */
    @FunctionalInterface
    public interface Sink {
        void accept(DirectBuffer buffer, int offset, int length);
    }

    private BookSnapshot() {
    }

    /**
     * One SnapshotHeader (with the order count), then one SnapshotOrder per resting
     * order.
     */
    public static void write(OrderBook book, MutableDirectBuffer scratch, Sink sink) {
        final MessageHeaderEncoder header = new MessageHeaderEncoder();

        final SnapshotHeaderEncoder snapshotHeader = new SnapshotHeaderEncoder();
        snapshotHeader.wrapAndApplyHeader(scratch, 0, header).orderCount(book.restingOrders());
        sink.accept(scratch, 0, MessageHeaderEncoder.ENCODED_LENGTH + snapshotHeader.encodedLength());

        final SnapshotOrderEncoder orderEncoder = new SnapshotOrderEncoder();
        book.forEachResting((id, side, price, remaining) -> {
            orderEncoder.wrapAndApplyHeader(scratch, 0, header)
                    .orderId(id)
                    .side(toWire(side))
                    .price(price)
                    .remaining(remaining);
            sink.accept(scratch, 0, MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength());
        });
    }

    /**
     * Feed it snapshot messages in order; isComplete() turns true after the last
     * one.
     */
    public static final class Loader {
        private final OrderBook book;
        private final MessageHeaderDecoder header = new MessageHeaderDecoder();
        private final SnapshotHeaderDecoder snapshotHeader = new SnapshotHeaderDecoder();
        private final SnapshotOrderDecoder orderDecoder = new SnapshotOrderDecoder();
        private int expected = -1;
        private int loaded = 0;

        public Loader(OrderBook book) {
            this.book = book;
        }

        public void onMessage(DirectBuffer buffer, int offset, int length) {
            header.wrap(buffer, offset);
            final int body = offset + header.encodedLength();

            switch (header.templateId()) {
                case SnapshotHeaderDecoder.TEMPLATE_ID -> {
                    snapshotHeader.wrap(buffer, body, header.blockLength(), header.version());
                    expected = snapshotHeader.orderCount();
                }
                case SnapshotOrderDecoder.TEMPLATE_ID -> {
                    orderDecoder.wrap(buffer, body, header.blockLength(), header.version());
                    book.restore(orderDecoder.orderId(), fromWire(orderDecoder.side()),
                            orderDecoder.price(), orderDecoder.remaining());
                    loaded++;
                }
                default -> throw new IllegalArgumentException(
                        "Unexpected template id in snapshot: " + header.templateId());
            }
        }

        public boolean isComplete() {
            return expected >= 0 && loaded == expected;
        }

        public int loadedOrders() {
            return loaded;
        }
    }

    private static OrderSide toWire(Side side) {
        return side == Side.BUY ? OrderSide.BUY : OrderSide.SELL;
    }

    private static Side fromWire(OrderSide side) {
        return switch (side) {
            case BUY -> Side.BUY;
            case SELL -> Side.SELL;
            default -> throw new IllegalArgumentException("Unknown side " + side);
        };
    }
}