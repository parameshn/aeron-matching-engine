# Aeron Matching Engine

A limit-order matching engine in Java. The matching core is plain Java with no
messaging dependencies. It runs as a replicated state machine on
[Aeron Cluster](https://github.com/aeron-io/aeron), and its messages are encoded
with Simple Binary Encoding (SBE).

## Versions

| Tag    | What it adds                                                                                               |
| ------ | ---------------------------------------------------------------------------------------------------------- |
| `v1.0` | Standalone order book: limit orders, price-time priority, partial fills, cancel, determinism tests         |
| `v2.0` | SBE schema, generated codecs, and an adapter that turns bytes into engine calls and events back into bytes |
| `v3.0` | The engine running as a service on a single-node Aeron Cluster                                             |
| `v3.1` | Three-node Aeron Cluster with leader election                                                              |

Planned for `v4.0`: real snapshots, replay and determinism checks against the log, and latency benchmarks.

## Architecture

```
cluster   Aeron: ClusterNode, MatchingService, OrderClient
   │
   ▼
codec     SBE schema, generated encoders/decoders, EngineAdapter
   │
   ▼
engine    OrderBook, Order, Level, Event, NewOrder, Side
```

Dependencies point downward only. `engine` has no Aeron or SBE imports, so the
matching logic is tested with plain JUnit.

One order, end to end:

```
OrderClient → cluster ingress (ordered into the log) → MatchingService
  → EngineAdapter (decode SBE) → OrderBook.submit → List<Event>
  → EngineAdapter (encode ExecutionReport) → cluster egress → OrderClient
```

## Requirements

- JDK 21
- Gradle 9.x
- Aeron 1.46.0, SBE 1.33.0, Agrona 1.23.0 (Aeron, SBE and Agrona versions must match; see the Aeron release notes before upgrading any one of them)

## Build and test

```bash
gradle build
```

## Run the cluster

Delete `cluster/cluster-data` first for a clean book. Start three nodes, each in
its own terminal, within a few seconds of each other:

```bash
gradle :cluster:runNode -Pnode=0
gradle :cluster:runNode -Pnode=1
gradle :cluster:runNode -Pnode=2
```

A cluster needs two of the three nodes up before it can elect a leader. When
each prints `Node N running`, send three orders:

```bash
gradle :cluster:runClient
```

Expected output on an empty book:

```
ACCEPTED  order=1 counterparty=0 price=100 qty=5
TRADE     order=2 counterparty=1 price=100 qty=3
TRADE     order=3 counterparty=1 price=100 qty=2
ACCEPTED  order=3 counterparty=0 price=105 qty=2
```

The book belongs to the cluster, not the client. Running the client again without
deleting `cluster-data` meets the orders left from the previous run, and the
output changes accordingly.

## What was verified

- All three nodes applied the same orders at the same log positions. Only the
  leader replies to the client.
- Leadership moved to another node between client runs, and the following run
  produced the output expected from the book as it stood before the change.

## Design decisions

- **Deterministic engine:** no clock or randomness, so every node produces the
  same events from the same log.
- **Integer prices and quantities:** no floating point.
- **Bad input becomes an event:** invalid orders produce `REJECTED` rather than an exception.
- **Flat event shape:** `Event` maps field for field onto the SBE `ExecutionReport`.
- **Reused codec objects:** `EngineAdapter` creates its encoders and decoders once.

## Known limitations

- The order book allocates on every call (events, orders, boxed keys). Measured
  optimization is planned for `v4.0`.
- Snapshots are empty, so a restarted node replays the whole log.
- Order ids can be reused once an order has been filled or cancelled.
- Limit orders only: no market orders or order modification.
- Cancel is O(n) within a price level.

## Alternatives considered

Aeron and SBE are a matched pair for low-latency Java. Other options include
Chronicle Queue for persisted messaging, the LMAX Disruptor for in-process
queues, a Raft library for consensus only, and Protocol Buffers or FlatBuffers
for encoding. The choice here favors one coherent stack with transport, archive
and cluster consensus together.

## Features

- Price-time priority matching
- Order cancellation
- Deterministic execution
- SBE message encoding and decoding
- Replicated execution on a three-node Aeron Cluster
- Unit, codec round-trip and adapter tests