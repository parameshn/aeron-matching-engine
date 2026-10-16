# Aeron Matching Engine

A Java-based order matching engine that supports order matching, cancellation, and price-time priority for trading systems.

## Requirements

- JDK 21
- Gradle (or use the Gradle wrapper if added)

## Run tests

```bash
gradle test
```

## Structure

- `OrderBook` coordinates order submission, matching, and cancellation.
- `Order` and `Level` are package-private implementation details.
- Tests cover matching, cancellation, and deterministic behavior.


