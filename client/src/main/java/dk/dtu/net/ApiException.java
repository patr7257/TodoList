package dk.dtu.net;

/**
 * Thrown when the todo HTTP API returns a non-2xx response. Carries the HTTP
 * status code and the raw response body so callers can react (for example,
 * {@code status == 401} means the session expired and the user must re-login).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String body;

    public ApiException(int status, String body, String message) {
        super(message);
        this.status = status;
        this.body = body;
    }

    /** HTTP status code of the failing response. */
    public int status() {
        return status;
    }

    /** Raw response body (may be empty), useful for surfacing server messages. */
    public String body() {
        return body;
    }

    /** True when the API rejected the request because the session is not valid. */
    public boolean isUnauthorized() {
        return status == 401;
    }
}
