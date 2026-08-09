package dk.dtu.net;

/**
 * One public share link for a list (issue #52), mirroring the API's share
 * object shape exactly so Gson binds it directly: {@code id, label, url,
 * token, createdAt, expiresAt, lastViewedAt, viewCount}. {@code label},
 * {@code expiresAt} and {@code lastViewedAt} may be null. Gson ignores any
 * extra key the API response carries that is not a field here, so this stays
 * forward compatible as the API grows.
 *
 * <p>{@code url} is the field the client must display: the client never
 * builds a share URL itself, so the desktop and web editions cannot disagree
 * about it.
 */
public record ShareDto(
        String id,
        String label,
        String url,
        String token,
        String createdAt,
        String expiresAt,
        String lastViewedAt,
        int viewCount) {
}
