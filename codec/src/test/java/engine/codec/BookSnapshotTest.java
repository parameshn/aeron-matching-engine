package engine.codec;

import engine.NewOrder;
import engine.OrderBook;
import engine.Side;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookSnapshotTest {

    private static List<byte[]> capture(OrderBook book) {
        List<byte[]> messages = new ArrayList<>();
        BookSnapshot.write(book, new ExpandableArrayBuffer(256), (buffer, offset, length) -> {
            byte[] copy = new byte[length];
            buffer.getBytes(offset, copy);
            messages.add(copy);
        });
        return messages;
    }

    private static OrderBook load(List<byte[]> messages) {
        OrderBook book = new OrderBook();
        BookSnapshot.Loader loader = new BookSnapshot.Loader(book);
        for (byte[] message : messages) {
            assertFalse(loader.isComplete());
            loader.onMessage(new UnsafeBuffer(message), 0, message.length);
        }
        assertTrue(loader.isComplete());
        return book;
    }

    @Test
    void emptyBookIsOneHeaderMessage() {
        List<byte[]> messages = capture(new OrderBook());

        assertEquals(1, messages.size());
        assertEquals(0, load(messages).restingOrders());
    }

    @Test
    void booksRoundTripThroughSbe() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.BUY, 99, 5));
        book.submit(new NewOrder(2, Side.BUY, 100, 7));
        book.submit(new NewOrder(3, Side.SELL, 103, 2));
        book.submit(new NewOrder(4, Side.SELL, 103, 4));

        List<byte[]> messages = capture(book);
        OrderBook restored = load(messages);

        assertEquals(1 + book.restingOrders(), messages.size());
        assertEquals(book.restingOrders(), restored.restingOrders());
        assertEquals(book.stateHash(), restored.stateHash());
    }

    @Test
    void restoredBookKeepsMatchingPriority() {
        var book = new OrderBook();
        book.submit(new NewOrder(1, Side.SELL, 100, 3));
        book.submit(new NewOrder(2, Side.SELL, 100, 3));

        OrderBook restored = load(capture(book));

        assertEquals(book.submit(new NewOrder(3, Side.BUY, 100, 4)),
                restored.submit(new NewOrder(3, Side.BUY, 100, 4)));
        assertEquals(book.stateHash(), restored.stateHash());
    }

    @Test
    void loaderRejectsUnknownTemplate() {
        UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
        buffer.putShort(2, (short) 99); // templateId sits at byte offset 2 of the header

        var loader = new BookSnapshot.Loader(new OrderBook());

        assertThrows(IllegalArgumentException.class, () -> loader.onMessage(buffer, 0, 64));
    }
}