package engine;

/** Receives resting orders in a fixed, repeatable order. */
@FunctionalInterface
public interface RestingOrderVisitor {
    void onOrder(long orderId, Side side, long price, long remaining);
}