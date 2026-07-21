package dk.dtu.net;

import java.util.List;

/**
 * Typed views of the todo HTTP API JSON shapes. Field names match the API
 * contract (camelCase) exactly so Gson binds them directly. See the API's
 * {@code Views}/controllers for the source of truth.
 *
 * <p>These are deliberately simple carriers; the {@code dk.dtu.methods} layer
 * maps them onto the UI's {@link dk.dtu.methods.Helpers.ListEntry} and
 * {@link dk.dtu.methods.Helpers.TaskEntry}.
 */
public final class ApiModels {

    private ApiModels() {
    }

    /** {id, name} entry from GET /state's users array (assignee options). */
    public record UserRef(String id, String name) {
    }

    /** {id, name, email} of the authenticated user. */
    public record CurrentUser(String id, String name, String email) {
    }

    /** One item (task) row, mirroring the API item view. */
    public record ItemDto(
            String id,
            String listId,
            String text,
            String description,
            boolean done,
            String status,
            Integer priority,
            String dueAt,
            String location,
            String assigneeId,
            int sort,
            String createdBy,
            String createdAt,
            String updatedAt,
            Integer year,
            String assigneeName) {
    }

    /** One list row, with its nested items and derived completion percentage. */
    public record ListDto(
            String id,
            String name,
            int sort,
            String createdAt,
            String owner,
            Integer priority,
            Integer year,
            String location,
            String description,
            String taskColumnsJson,
            Integer completionPercentage,
            List<ItemDto> items) {
    }

    /** GET /state response: current user, all users, all lists (with items). */
    public record StateResponse(
            CurrentUser user,
            List<UserRef> users,
            List<ListDto> lists) {
    }

    /** POST /login response. */
    public record LoginResponse(boolean ok, CurrentUser user, String token) {
    }
}
