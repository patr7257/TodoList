package dk.dtu.api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CompletionTest {

    private static ItemRow withStatus(String status) {
        return new ItemRow("id", "list", "t", null, "DONE".equals(status), status,
                null, null, null, null, 0, null, null, null, null);
    }

    @Test
    void emptyListIsZero() {
        assertEquals(0, Completion.forItems(List.of()));
        assertEquals(0, Completion.forItems(null));
    }

    @Test
    void mapsEachStatusToItsPercentage() {
        assertEquals(0, Completion.percentFor("NOT_STARTED"));
        assertEquals(50, Completion.percentFor("IN_PROGRESS"));
        assertEquals(50, Completion.percentFor("DELAYED"));
        assertEquals(50, Completion.percentFor("NEED_HELP"));
        assertEquals(100, Completion.percentFor("DONE"));
        assertEquals(0, Completion.percentFor("BOGUS"));
        assertEquals(0, Completion.percentFor(null));
    }

    @Test
    void averagesItemPercentagesRounded() {
        // 0 + 50 + 100 = 150 / 3 = 50
        assertEquals(50, Completion.forItems(List.of(
                withStatus("NOT_STARTED"), withStatus("IN_PROGRESS"), withStatus("DONE"))));
        // 100 + 100 = 200 / 2 = 100
        assertEquals(100, Completion.forItems(List.of(withStatus("DONE"), withStatus("DONE"))));
        // 0 + 0 + 100 = 100 / 3 = 33.33 -> 33
        assertEquals(33, Completion.forItems(List.of(
                withStatus("NOT_STARTED"), withStatus("NOT_STARTED"), withStatus("DONE"))));
    }
}
