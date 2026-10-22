# Aeron Matching Engine

A Java-based order matching engine that supports order matching, cancellation, and price-time priority, with SBE-based message encoding and decoding.

## Versions

- **v1.0:** Standalone order book with limit orders, cancellation, and determinism tests.
- **v2.0:** Adds SBE message schemas, generated codecs, and an engine adapter for message processing and event reporting.

## Requirements

- JDK 21
- Gradle

## Run Tests

```bash
gradle test
```

## Modules

- `engine`: Order book and matching logic.
- `codec`: SBE schema and generated message codecs.
- `EngineAdapter`: Connects message decoding with order processing and event encoding.

## Features

- Price-time priority matching
- Order cancellation
- Deterministic execution
- SBE message encoding and decoding
- Unit and codec round-trip tests
