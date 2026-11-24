package engine.cluster;

import engine.OrderBook;
import engine.codec.BookSnapshot;
import engine.codec.EngineAdapter;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

/**
 * Runs the order book as a replicated state machine.
 * Every node applies the same messages in the same order to its own book.
 * Start a node with -Plog=true to print what each node applies, plus a hash of
 * its book.
 */
public final class MatchingService implements ClusteredService {

    private static final boolean LOG = Boolean.getBoolean("engine.log");

    private final OrderBook book = new OrderBook();
    private final EngineAdapter adapter = new EngineAdapter(book);
    private final ExpandableArrayBuffer out = new ExpandableArrayBuffer(1024);
    private final ExpandableArrayBuffer snapshotBuffer = new ExpandableArrayBuffer(256);
    private Cluster cluster;

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        if (snapshotImage != null) {
            loadSnapshot(snapshotImage);
            if (LOG) {
                System.out.printf("snapshot loaded: %d resting orders, hash=%016x%n", book.restingOrders(),
                        book.stateHash());
            }
        }
    }

    private void loadSnapshot(Image image) {
        final BookSnapshot.Loader loader = new BookSnapshot.Loader(book);
        final FragmentHandler handler = (buffer, offset, length, header) -> loader.onMessage(buffer, offset, length);

        while (!loader.isComplete()) {
            final int fragments = image.poll(handler, 1);
            if (fragments == 0 && (image.isClosed() || image.isEndOfStream())) {
                throw new IllegalStateException(
                        "snapshot ended before all data was loaded (" + loader.loadedOrders() + " orders)");
            }
            cluster.idleStrategy().idle(fragments);
        }
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) {
    }

    @Override
    public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason) {
    }

    @Override
    public void onSessionMessage(
            ClientSession session, long timestamp,
            DirectBuffer buffer, int offset, int length, Header header) {

        // Deterministic: no clock, no randomness. The same input gives the same output
        // on every node.
        final int written = adapter.onMessage(buffer, offset, out, 0);

        if (LOG) {
            System.out.printf("[node %d %s] log position %d, applied %d bytes, resting=%d hash=%016x%n",
                    cluster.memberId(), cluster.role(), cluster.logPosition(), length,
                    book.restingOrders(), book.stateHash());
        }
        if (written > 0) {
            reply(session, written);
        }
    }

    private void reply(ClientSession session, int length) {
        // Only the leader actually sends. On followers offer() returns a positive
        // placeholder.
        while (session.offer(out, 0, length) < 0) {
            cluster.idleStrategy().idle();
        }
    }

    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        BookSnapshot.write(book, snapshotBuffer, (buffer, offset, length) -> {
            long result;
            while ((result = snapshotPublication.offer(buffer, offset, length)) < 0) {
                if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
                    throw new IllegalStateException("snapshot offer failed: " + result);
                }
                cluster.idleStrategy().idle();
            }
        });
        if (LOG) {
            System.out.printf("[node %d] snapshot written: %d resting orders, hash=%016x%n",
                    cluster.memberId(), book.restingOrders(), book.stateHash());
        }
    }

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        if (LOG) {
            System.out.printf("[node %d] role is now %s%n", cluster.memberId(), newRole);
        }
    }

    @Override
    public void onTerminate(Cluster cluster) {
    }
}