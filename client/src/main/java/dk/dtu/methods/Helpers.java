package dk.dtu.methods;

import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.shared.Defaults;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Helper data structures and HTTP <-> UI mapping utilities.
//
// This used to build/await jSpace tuples. The client now talks to the shared
// HTTP API (see dk.dtu.net), so the request plumbing is gone and Helpers just
// carries the UI row types (ListEntry, TaskEntry) plus the mapping from the API
// DTOs and the date conversions the UI expects.
public class Helpers {

    private Helpers() {
    }

    // The UI due-date column uses LocalDate.toString(), i.e. "yyyy-MM-dd".
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    // -- date conversions ------------------------------------------------------

    /**
     * Convert an API ISO-8601 instant (for example {@code 2026-07-20T00:00:00Z})
     * to the "yyyy-MM-dd" string the desktop due-date column uses. Empty string
     * when the instant is null/blank/unparseable.
     */
    public static String isoInstantToDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }
        try {
            Instant instant = Instant.parse(iso);
            return DATE.format(instant.atZone(ZoneOffset.UTC).toLocalDate());
        } catch (Exception e) {
            // Some servers may already return a plain date; keep the first 10 chars.
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }

    /**
     * Convert a "yyyy-MM-dd" date string to an ISO-8601 instant at UTC midnight
     * (for example {@code 2026-07-20T00:00:00Z}), which the API accepts. Returns
     * null when the input is blank (the API treats null as "no due date").
     */
    public static String dateToIsoInstant(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(date.trim());
            return DateTimeFormatter.ISO_INSTANT.format(d.atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (Exception e) {
            // Already an instant or another accepted form: pass through unchanged.
            return date.trim();
        }
    }

    // -- DTO -> UI mapping -----------------------------------------------------

    /** Map an API list (with its items) to the list-overview row. */
    public static ListEntry toListEntry(ListDto l) {
        int priority = l.priority() != null ? l.priority() : Defaults.PRIORITY;
        int year = l.year() != null ? l.year() : Defaults.YEAR;
        int completion = l.completionPercentage() != null ? l.completionPercentage() : 0;

        int taskCount = 0;
        int overdueCount = 0;
        LocalDate today = LocalDate.now();
        if (l.items() != null) {
            taskCount = l.items().size();
            for (ItemDto it : l.items()) {
                if (it == null) {
                    continue;
                }
                String due = isoInstantToDate(it.dueAt());
                if (!due.isBlank() && !"DONE".equals(it.status())) {
                    try {
                        if (LocalDate.parse(due).isBefore(today)) {
                            overdueCount++;
                        }
                    } catch (Exception ignored) {
                        // skip unparseable dates
                    }
                }
            }
        }

        return new ListEntry(
                l.id(),
                l.name(),
                safe(l.owner()),
                l.taskColumnsJson() != null ? l.taskColumnsJson() : "",
                priority,
                year,
                l.sort(),
                safe(l.location()),
                safe(l.description()),
                completion,
                taskCount,
                overdueCount);
    }

    /** Map an API item to the task row (owner column shows the assignee name). */
    public static TaskEntry toTaskEntry(ItemDto it) {
        int priority = it.priority() != null ? it.priority() : Defaults.PRIORITY;
        int year = it.year() != null ? it.year() : Defaults.YEAR;
        return new TaskEntry(
                it.listId(),
                it.id(),
                it.text(),
                safe(it.assigneeName()),
                it.status(),
                isoInstantToDate(it.dueAt()),
                priority,
                year,
                it.sort(),
                safe(it.location()),
                safe(it.description()));
    }

    /** All items of a list mapped to task rows, ordered by their sort field. */
    public static List<TaskEntry> toTaskEntries(ListDto l) {
        List<TaskEntry> out = new ArrayList<>();
        if (l != null && l.items() != null) {
            List<ItemDto> items = new ArrayList<>(l.items());
            items.sort((a, b) -> Integer.compare(a.sort(), b.sort()));
            for (ItemDto it : items) {
                if (it != null) {
                    out.add(toTaskEntry(it));
                }
            }
        }
        return out;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    // Represents a todo list entry with ID and name
    public static class ListEntry {
        public final String id;
        public final String name;
        public final String owner;
        public final String taskColumnsJson;
        public final int priority;
        public final int year;
        public final int orderIndex;
        public final String location;
        public final String description;
        public int completionPercentage;
        public final int taskCount;
        public final int overdueTaskCount;

        public ListEntry(String id, String name, String owner, String taskColumnsJson, int priority, int year, int orderIndex, String location, String description, int completionPercentage, int taskCount, int overdueTaskCount) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.taskColumnsJson = taskColumnsJson;
            this.priority = priority;
            this.year = year;
            this.orderIndex = orderIndex;
            this.location = location;
            this.description = description;
            this.completionPercentage = completionPercentage;
            this.taskCount = taskCount;
            this.overdueTaskCount = overdueTaskCount;
        }

        @Override
        public String toString() {
            return id + " - " + name;
        }
    }

    // Represents a task entry with all task details
    public static class TaskEntry {
        public final String listId;
        public final String id;
        public final String title;
        public final String owner;
        public final String status;
        public final String dueDate;
        public final int priority;
        public final int year;
        public final int orderIndex;
        public final String location;
        public final String description;

        public TaskEntry(String listId, String id, String title,
                String owner, String status, String dueDate, int priority, int year, int orderIndex, String location, String description) {
            this.listId = listId;
            this.id = id;
            this.title = title;
            this.owner = owner;
            this.status = status;
            this.dueDate = dueDate;
            this.priority = priority;
            this.year = year;
            this.orderIndex = orderIndex;
            this.location = location;
            this.description = description;
        }

        // Pretty status text for the UI
        public String statusToString() {
            if (status == null)
                return "";

            return switch (status.trim().toUpperCase()) {
                case "NOT_STARTED" -> "Not started yet";
                case "IN_PROGRESS" -> "In progress";
                case "DELAYED" -> "Delayed";
                case "NEED_HELP" -> "Needs help";
                case "DONE" -> "Done";
                default -> status; // fallback
            };
        }

        // Task name for the "Task" column
        public String nameToString() {
            return title != null ? title : "";
        }

        // Owner for the "Owner" column
        public String ownerToString() {
            return (owner == null || owner.isBlank()) ? "" : owner;
        }

        // Optional: nice due date text if you ever want it
        public String dueDateToString() {
            return (dueDate == null || dueDate.isBlank()) ? "" : dueDate;
        }

        public String locationToString() {
            return (location == null || location.isBlank()) ? "" : location;
        }

        public String descriptionToString() {
            return (description == null || description.isBlank()) ? "" : description;
        }

        @Override
        public String toString() {
            String who = ownerToString();
            String due = (dueDate == null || dueDate.isBlank()) ? "" : " (due: " + dueDate + ")";
            return nameToString() + " " + who + " [" + status + "]" + due;
        }
    }
}
