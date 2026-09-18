package dk.dtu.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import dk.dtu.api.domain.TodoService;

import org.junit.jupiter.api.Test;

/**
 * The pure half of the bulk reorder routes (issue #77): what
 * {@code {"order":[{"id","sort"}]}} is accepted, and what is a 400 before a
 * single row is written. No database and no port, so every rejection below is
 * proven to happen in the parser rather than somewhere downstream.
 */
class OrderBodyTest {

    private static final String ID_A = UUID.randomUUID().toString();
    private static final String ID_B = UUID.randomUUID().toString();

    private static List<TodoService.SortEntry> parse(String json) {
        return OrderBody.parse(Body.parse(json));
    }

    private static void rejects(String json, String why) {
        HttpError e = assertThrows(HttpError.class, () -> parse(json), why);
        assertEquals(400, e.status(), why);
    }

    @Test
    void parsesEntriesInTheOrderGiven() {
        List<TodoService.SortEntry> entries = parse(
                "{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":2},{\"id\":\"" + ID_B + "\",\"sort\":0}]}");

        assertEquals(2, entries.size());
        assertEquals(ID_A, entries.get(0).id());
        assertEquals(2, entries.get(0).sort());
        assertEquals(ID_B, entries.get(1).id());
        assertEquals(0, entries.get(1).sort());
    }

    @Test
    void acceptsAnEmptyOrderAsANoOp() {
        assertTrue(parse("{\"order\":[]}").isEmpty(),
                "a reorder that moved nothing is not an error");
    }

    @Test
    void acceptsNegativeSortValues() {
        // Dropping something above everything else is a legitimate way for a
        // client to avoid renumbering the whole arrangement.
        assertEquals(-1, parse("{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":-1}]}").get(0).sort());
    }

    @Test
    void ignoresExtraKeysInsideAnEntryAndInTheBody() {
        // Notably a "userId": the caller is the token, never the body, so an
        // unknown key must be ignored rather than honoured.
        List<TodoService.SortEntry> entries = parse(
                "{\"userId\":\"" + ID_B + "\",\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":0,\"userId\":\"" + ID_B + "\"}]}");
        assertEquals(1, entries.size());
        assertEquals(ID_A, entries.get(0).id());
    }

    @Test
    void rejectsAMissingOrNonArrayOrder() {
        rejects("{}", "an absent order is a 400");
        rejects("{\"order\":null}", "a null order is a 400");
        rejects("{\"order\":\"nope\"}", "a string order is a 400");
        rejects("{\"order\":{\"id\":\"" + ID_A + "\"}}", "an object order is a 400");
    }

    @Test
    void rejectsAMalformedEntry() {
        rejects("{\"order\":[\"" + ID_A + "\"]}", "a bare string entry is a 400");
        rejects("{\"order\":[{\"sort\":0}]}", "a missing id is a 400");
        rejects("{\"order\":[{\"id\":null,\"sort\":0}]}", "a null id is a 400");
        rejects("{\"order\":[{\"id\":\"not-a-uuid\",\"sort\":0}]}", "a non-uuid id is a 400");
        rejects("{\"order\":[{\"id\":\"" + ID_A + "\"}]}", "a missing sort is a 400");
        rejects("{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":\"0\"}]}", "a string sort is a 400");
        rejects("{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":1.5}]}", "a fractional sort is a 400");
        rejects("{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":2147483648}]}",
                "a sort outside the integer column's range is a 400, not a silent overflow");
    }

    @Test
    void rejectsAMalformedEntryEvenWhenItIsNotTheFirst() {
        // The whole batch is one transaction, so a bad row halfway down must
        // take the WHOLE request down rather than letting the rows before it
        // through.
        rejects("{\"order\":[{\"id\":\"" + ID_A + "\",\"sort\":0},{\"id\":\"nope\",\"sort\":1}]}",
                "a bad row in the middle rejects the whole batch");
    }

    @Test
    void rejectsABatchOverTheCap() {
        StringBuilder sb = new StringBuilder("{\"order\":[");
        for (int i = 0; i <= OrderBody.MAX_ENTRIES; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(UUID.randomUUID()).append("\",\"sort\":").append(i).append('}');
        }
        sb.append("]}");
        rejects(sb.toString(), "a batch over MAX_ENTRIES is a 400");
    }
}
