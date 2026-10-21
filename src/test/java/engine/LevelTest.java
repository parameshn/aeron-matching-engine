package engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelTest {

    @Test
    void newLevelIsEmpty() {
        var level = new Level();

        assertTrue(level.isEmpty());
        assertEquals(0, level.size());
        assertNull(level.peek());
    }

    @Test
    void keepsArrivalOrder() {
        var level = new Level();
        var a = new Order(1, Side.SELL, 100, 3);
        var b = new Order(2, Side.SELL, 100, 3);
        level.add(a);
        level.add(b);

        assertSame(a, level.peek());
        assertSame(a, level.pollFirst());
        assertSame(b, level.peek());
    }

    @Test
    void removeTakesOutAnOrderFromTheMiddle() {
        var level = new Level();
        var a = new Order(1, Side.SELL, 100, 3);
        var b = new Order(2, Side.SELL, 100, 3);
        var c = new Order(3, Side.SELL, 100, 3);
        level.add(a);
        level.add(b);
        level.add(c);

        assertTrue(level.remove(b));
        assertEquals(2, level.size());
        assertSame(a, level.pollFirst());
        assertSame(c, level.pollFirst());
    }

    @Test
    void removeReturnsFalseForOrderNotInLevel() {
        var level = new Level();

        assertFalse(level.remove(new Order(1, Side.SELL, 100, 3)));
    }
}