package dk.dtu.api.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dk.dtu.api.domain.Completion;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.ShareRow;

/**
 * JSON shapes for public share links (issue #52), as ordered maps.
 *
 * <p><b>This class must never call {@link Views}, and that is the single most
 * important rule in it.</b> {@code Views.list} and {@code Views.item} feed
 * {@code GET /api/todo/state}, which is APPEND-ONLY: new fields get appended
 * there routinely, by people who are thinking about the two authenticated
 * clients and not about this file. If the public payload were built by
 * delegating to those methods, the very next field appended to {@code /state}
 * would become world readable the moment it merged, silently, with no diff on
 * this file to review. So every field below is written out by hand. Adding one
 * to the public payload has to be an explicit, reviewable act.
 *
 * <p>Fields that must NEVER appear in the public payload:
 * <ul>
 *   <li>list: {@code id}, {@code owner}, {@code ownerId}, {@code ownerName},
 *       {@code location}, {@code taskColumnsJson}, {@code priority},
 *       {@code year}, {@code sort}, {@code createdAt}</li>
 *   <li>item: {@code listId}, {@code assigneeId}, {@code assigneeName},
 *       {@code createdBy}, {@code location}, {@code dueAt}, {@code priority},
 *       {@code year}, {@code createdAt}, {@code updatedAt}, {@code sort}</li>
 * </ul>
 *
 * <p>The reasons are concrete, not theoretical. No {@code users.id} value may
 * ever cross an unauthenticated boundary, which rules out {@code ownerId},
 * {@code assigneeId} and {@code createdBy}. {@code location} can be a home
 * address. Item {@code priority} on a wishlist would quietly tell the recipient
 * which present is wanted most. The list {@code id} is what every authenticated
 * route is keyed on, so publishing it hands out the one value an attacker would
 * need if any other route were ever mis-guarded.
 *
 * <p>{@code sharedBy} is a display NAME only, never an id and never an email.
 * Timestamps go through the package-private {@link Views#iso(Instant)} so all
 * three surfaces agree on the ISO-8601 UTC format (that one shared formatter is
 * a format decision, not a payload shape decision, so it is not the coupling
 * the rule above forbids).
 */
public final class ShareViews {

    private ShareViews() {
    }

    /**
     * The whole public payload: {@code {list: {...}, items: [...]}}.
     *
     * <p>Key order is pinned by {@code ShareViewsTest} and by
     * {@code SharesIntegrationTest}: list is
     * {name, description, sharedBy, itemCount, doneCount, completionPercentage,
     * expiresAt}, each item is {id, text, description, done, status}.
     */
    public static Map<String, Object> publicPayload(ListRow list, List<ItemRow> items,
                                                    String sharedBy, Instant expiresAt) {
        List<ItemRow> safeItems = items == null ? List.of() : items;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", publicList(list, safeItems, sharedBy, expiresAt));
        List<Map<String, Object>> itemViews = new ArrayList<>(safeItems.size());
        for (ItemRow item : safeItems) {
            itemViews.add(publicItem(item));
        }
        out.put("items", itemViews);
        return out;
    }

    /** {name, description, sharedBy, itemCount, doneCount, completionPercentage, expiresAt} */
    public static Map<String, Object> publicList(ListRow list, List<ItemRow> items,
                                                 String sharedBy, Instant expiresAt) {
        List<ItemRow> safeItems = items == null ? List.of() : items;

        int doneCount = 0;
        for (ItemRow item : safeItems) {
            if (item.done()) {
                doneCount++;
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", list == null ? null : list.name());
        m.put("description", list == null ? null : list.description());
        // Display name only. Null when the list has no resolvable owner, but the
        // key is still emitted, matching how Views includes null-valued keys so
        // a client can rely on the shape rather than on presence.
        m.put("sharedBy", sharedBy);
        m.put("itemCount", safeItems.size());
        m.put("doneCount", doneCount);
        // Always the shared Completion derivation, never a local re-implementation:
        // this is the THIRD surface showing a completion number, and the only way
        // it cannot disagree with the desktop and web clients is by asking the
        // same function.
        m.put("completionPercentage", Completion.forItems(safeItems));
        m.put("expiresAt", Views.iso(expiresAt));
        return m;
    }

    /** {id, text, description, done, status} */
    public static Map<String, Object> publicItem(ItemRow item) {
        Map<String, Object> m = new LinkedHashMap<>();
        // The item id is safe to publish and is needed as a stable render key:
        // unlike the list id it unlocks nothing, because no route accepts an
        // item id from an unauthenticated caller.
        m.put("id", item.id());
        m.put("text", item.text());
        m.put("description", item.description());
        m.put("done", item.done());
        m.put("status", item.status());
        return m;
    }

    /**
     * The AUTHENTICATED management view of a share:
     * {id, label, url, token, createdAt, expiresAt, lastViewedAt, viewCount}.
     *
     * <p>{@code url} is composed here and only here, from the API's configured
     * share base. Neither client ever builds a share URL from a token, which is
     * what makes it structurally impossible for the desktop app and the website
     * to show different links for the same share.
     */
    public static Map<String, Object> share(ShareRow s, String shareBaseUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("label", s.label());
        m.put("url", url(shareBaseUrl, s.token()));
        m.put("token", s.token());
        m.put("createdAt", Views.iso(s.createdAt()));
        m.put("expiresAt", Views.iso(s.expiresAt()));
        m.put("lastViewedAt", Views.iso(s.lastViewedAt()));
        m.put("viewCount", s.viewCount());
        return m;
    }

    /** {@code <base>/s/<token>}. The base already has any trailing slash stripped. */
    static String url(String shareBaseUrl, String token) {
        return (shareBaseUrl == null ? "" : shareBaseUrl) + "/s/" + token;
    }
}
