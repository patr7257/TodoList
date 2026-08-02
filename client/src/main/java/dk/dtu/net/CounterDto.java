package dk.dtu.net;

/**
 * One shared "fun counter" row (Total Flights, Total Ships, Tour de Brede
 * walks, ...), mirroring the API's {@code CounterViews.counter} shape exactly
 * so Gson binds it directly. See the API's {@code CounterViews}/
 * {@code CountersController} for the source of truth.
 */
public record CounterDto(
        String id,
        String label,
        String description,
        int value,
        String icon,
        int sort,
        String createdBy,
        String createdAt,
        String updatedAt) {
}
