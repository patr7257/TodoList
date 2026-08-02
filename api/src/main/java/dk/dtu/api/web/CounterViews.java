package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;

import dk.dtu.api.domain.CounterRow;

/**
 * Builds the JSON shape for a fun counter, as an ordered map (mirrors
 * {@link Views}). Timestamps go through the package-private {@link
 * Views#iso(java.time.Instant)} so both views agree on the ISO-8601 format.
 */
public final class CounterViews {

    private CounterViews() {
    }

    /** {id, label, description, value, icon, sort, createdBy, createdAt, updatedAt} */
    public static Map<String, Object> counter(CounterRow c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("label", c.label());
        m.put("description", c.description());
        m.put("value", c.value());
        m.put("icon", c.icon());
        m.put("sort", c.sort());
        m.put("createdBy", c.createdBy());
        m.put("createdAt", Views.iso(c.createdAt()));
        m.put("updatedAt", Views.iso(c.updatedAt()));
        return m;
    }
}
