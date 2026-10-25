package engine.cluster;

import engine.codec.ExecutionReportDecoder;
import engine.codec.MessageHeaderDecoder;
import engine.codec.MessageHeaderEncoder;
import engine.codec.NewOrderEncoder;
import engine.codec.OrderSide;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public final class OrderClient implements EgressListener {

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecutionReportDecoder reportDecoder = new ExecutionReportDecoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final NewOrderEncoder orderEncoder = new NewOrderEncoder();
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(256);

    @Override
    public void onMessage(
            long clusterSessionId, long timestamp,
            DirectBuffer buffer, int offset, int length, Header header) {
        // One egress message may hold several concatenated reports.
        int pos = offset;
        final int end = offset + length;
        while (pos < end) {
            headerDecoder.wrap(buffer, pos);
            reportDecoder.wrap(buffer, pos + headerDecoder.encodedLength(),
                    headerDecoder.blockLength(), headerDecoder.version());
            System.out.printf("%-9s order=%d counterparty=%d price=%d qty=%d%n",
                    reportDecoder.type(), reportDecoder.orderId(), reportDecoder.counterpartyId(),
                    reportDecoder.price(), reportDecoder.quantity());
            pos += headerDecoder.encodedLength() + headerDecoder.blockLength();
        }
    }

    private void send(AeronCluster cluster, long id, OrderSide side, long price, long qty) {
        orderEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .orderId(id).side(side).price(price).quantity(qty);
        final int length = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();
        while (cluster.offer(buffer, 0, length) < 0) {
            cluster.pollEgress();
            Thread.yield();
        }
        System.out.println("sent order " + id);
    }

    public static void main(String[] args) throws InterruptedException {
        final OrderClient client = new OrderClient();
        final String ingressEndpoints = ClusterConfig.ingressEndpoints(
                ClusterNode.HOSTNAMES, ClusterNode.PORT_BASE, ClusterConfig.CLIENT_FACING_PORT_OFFSET);

        try (MediaDriver driver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                     .threadingMode(ThreadingMode.SHARED)
                     .dirDeleteOnStart(true)
                     .dirDeleteOnShutdown(true));
             AeronCluster cluster = AeronCluster.connect(new AeronCluster.Context()
                     .egressListener(client)
                     .egressChannel("aeron:udp?endpoint=localhost:0")
                     .aeronDirectoryName(driver.aeronDirectoryName())
                     .ingressChannel("aeron:udp")
                     .ingressEndpoints(ingressEndpoints))) {

            client.send(cluster, 1, OrderSide.SELL, 100, 5);   // expect ACCEPTED
            client.send(cluster, 2, OrderSide.BUY, 105, 3);    // expect TRADE at 100 for 3
            client.send(cluster, 3, OrderSide.BUY, 105, 4);    // expect TRADE for 2, then ACCEPTED for 2

            final long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                cluster.pollEgress();
                Thread.sleep(10);
            }
        }
    }
}