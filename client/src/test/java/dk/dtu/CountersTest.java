package dk.dtu;

import dk.dtu.methods.Counters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Counters} input validation, mirroring {@code ListsTest}'s
 * style: every one of these throws before any network call is made, so no
 * {@link dk.dtu.net.ApiSession} wiring is needed.
 */
class CountersTest {

    @Test
    void createCounterRejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> Counters.createCounter("   ", null, null, null));
    }

    @Test
    void createCounterRejectsNullLabel() {
        assertThrows(IllegalArgumentException.class, () -> Counters.createCounter(null, null, null, null));
    }

    @Test
    void renameCounterRejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> Counters.renameCounter("c1", "   "));
    }

    @Test
    void renameCounterRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.renameCounter("", "New label"));
    }

    @Test
    void setDescriptionRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setDescription(null, "notes"));
    }

    @Test
    void setIconRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setIcon("   ", "fth-send"));
    }

    @Test
    void setValueRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setValue("", 5));
    }

    @Test
    void setSortRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setSort(null, 2));
    }

    @Test
    void bumpRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.bump("   ", 1));
    }

    @Test
    void bumpRejectsZeroDelta() {
        assertThrows(IllegalArgumentException.class, () -> Counters.bump("c1", 0));
    }

    @Test
    void deleteCounterRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> Counters.deleteCounter(null));
    }

    @Test
    void setCounterOrderBulkRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setCounterOrderBulk(java.util.List.of()));
    }

    @Test
    void setCounterOrderBulkRejectsNullList() {
        assertThrows(IllegalArgumentException.class, () -> Counters.setCounterOrderBulk(null));
    }

    @Test
    void updateAllRejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> Counters.updateAll("c1", "  ", null, 0, null));
    }
}
