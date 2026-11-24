# Aeron Matching Engine

A limit-order matching engine in Java. The matching core is plain Java with no
messaging dependencies. It runs as a replicated state machine on
[Aeron Cluster](https://github.com/aeron-io/aeron), and its messages are encoded
with Simple Binary Encoding (SBE).

## Features

- Price-time priority matching with partial fills
- Order cancellation
- Deterministic execution
- SBE message encoding and decoding
- Replicated execution on a three-node Aeron Cluster
- Book snapshots
- Latency benchmark (HdrHistogram)
- Unit, codec round-trip, adapter and replay determinism tests

## Architecture

```
cluster   Aeron: ClusterNode, MatchingService, OrderClient
   │
   ▼
codec     SBE schema, generated encoders/decoders, EngineAdapter, BookSnapshot
   │
   ▼
engine    OrderBook, Order, Level, Event, NewOrder, Side
```

Dependencies point downward only. `engine` has no Aeron or SBE imports, so the
matching logic is tested with plain JUnit.

One order, end to end:

```
OrderClient → cluster ingress → MatchingService → EngineAdapter (decode)
  → OrderBook.submit → List<Event> → EngineAdapter (encode) → cluster egress → OrderClient
```

## Replicated state machine

| Rule | Where |
| ---- | ----- |
| State is encoded internally | `OrderBook` owns all state; snapshot and restore via `BookSnapshot` |
| Each command addresses one method | `EngineAdapter` maps `NewOrder` to `submit` and `CancelOrder` to `cancel` |
| Methods are deterministic | No clock or randomness, integer math, replay determinism tests |
| Commands mutate state and/or produce events | `List<Event>` encoded as `ExecutionReport` |

## Requirements

- JDK 21
- Gradle 9.x
- Aeron 1.46.0, SBE 1.33.0, Agrona 1.23.0 (upgrade together)

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

Two of three nodes must be up to elect a leader. When each prints `Node N running`:

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
clearing `cluster-data` matches against the leftover orders.

Benchmark:

```bash
gradle :bench:runBench
```

## Design decisions

- **Deterministic engine:** no clock or randomness, so every node produces the same events from the same log.
- **Integer prices and quantities:** no floating point.
- **Bad input becomes an event:** invalid orders produce `REJECTED`, not an exception.
- **Flat event shape:** `Event` maps field for field onto the SBE `ExecutionReport`.
- **Reused codec objects:** `EngineAdapter` creates its encoders and decoders once.

## Known limitations

- The order book allocates on every call (events, orders, boxed keys).
- Order ids can be reused once an order has been filled or cancelled.
- Limit orders only: no market orders or order modification.
- Cancel is O(n) within a price level.

Run logs and node comparison output are in `docs/evidence/`.
