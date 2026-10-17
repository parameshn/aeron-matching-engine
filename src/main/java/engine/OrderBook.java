package engine;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Coordinates order submission, matching, and cancellation.
 */
public final class OrderBook {

    private final TreeMap<Long, Level> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, Level> asks = new TreeMap<>();
    private final Map<Long, Order> byId = new HashMap<>();

    


    /**
     * Submit a new order and return the events generated.
     */
    public List<Event> submit(NewOrder newOrder) {
        throw new UnsupportedOperationException("TODO: implement order matching");
    }

    void rest(Order o) {
        if (byId.containsKey(o.id))
            throw new IllegalArgumentException("duplicate id " + o.id);
        sideMap(o.side).computeIfAbsent(o.price, k -> new Level()).add(o);
        byId.put(o.id, o);
    }

    //** Returns true if the order existed and was removed. */
    boolean cancel(long orderId) {
        Order o = byId.remove(orderId);
        if (o == null) return false;
        TreeMap<Long, Level> map = sideMap(o.side);
        Level level = map.get(o.price);
        level.remove(o);
        if (level.isEmpty()) map.remove(o.price);
        return true;
    }

    Optional<Long> bestBid() { return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey()); }
    Optional<Long> bestAsk() { return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey()); }

    private TreeMap<Long, Level> sideMap(Side s) {
        return s == Side.BUY ? bids : asks;
    }

}
