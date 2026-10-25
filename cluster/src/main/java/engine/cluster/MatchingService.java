package engine.cluster;

import engine.OrderBook;
import engine.codec.EngineAdapter;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

/**
 * Runs the order book as a replicated state machine.
 * Every node receives the same messages in the same order and applies them to its own book.
 */
public final class MatchingService implements ClusteredService {

    private final EngineAdapter adapter = new EngineAdapter(new OrderBook());
    private final ExpandableArrayBuffer out = new ExpandableArrayBuffer(1024);
    private Cluster cluster;

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        // v3.0 writes no snapshot, so there is nothing to load. Snapshots arrive in v4.0.
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) { }

    @Override
    public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason) { }

    @Override
    public void onSessionMessage(
            ClientSession session, long timestamp,
            DirectBuffer buffer, int offset, int length, Header header) {

        // Deterministic: no clock, no randomness. The same input gives the same output on every node.
        final int written = adapter.onMessage(buffer, offset, out, 0);
        if (written > 0) {
            reply(session, written);
        }
    }

    private void reply(ClientSession session, int length) {
        // Only the leader actually sends. On followers offer() returns a positive placeholder,
        // so the same code runs on every node.
        while (session.offer(out, 0, length) < 0) {
            cluster.idleStrategy().idle();
        }
    }

    @Override
    public void onTimerEvent(long correlationId, long timestamp) { }

    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        // Empty for v3.0. After a restart the node replays the log from the start.
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) { }

    @Override
    public void onTerminate(Cluster cluster) { }
}