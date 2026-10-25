package engine.cluster;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.concurrent.ShutdownSignalBarrier;

import java.io.File;
import java.util.List;

public final class ClusterNode {

    static final int PORT_BASE = 9000;
    // One entry per node. v3.0 runs a single node; v3.1 changes this to three entries.
    static final List<String> HOSTNAMES = List.of("localhost");

    public static void main(String[] args) {
        final int nodeId = Integer.parseInt(args[0]);

        final ClusterConfig config = ClusterConfig.create(
                nodeId, HOSTNAMES, PORT_BASE, new MatchingService());
        config.consensusModuleContext().ingressChannel("aeron:udp");
        config.baseDir(new File("cluster-data", "node" + nodeId));

        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        try (ClusteredMediaDriver driver = ClusteredMediaDriver.launch(
                     config.mediaDriverContext(),
                     config.archiveContext(),
                     config.consensusModuleContext());
             ClusteredServiceContainer container = ClusteredServiceContainer.launch(
                     config.clusteredServiceContext())) {
            System.out.println("Node " + nodeId + " running. Ctrl+C to stop.");
            barrier.await();
        }
    }
}