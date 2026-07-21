package dk.dtu.api.web;

/**
 * A controlled error that maps directly to an HTTP status and a JSON body of
 * {@code {"error": "<message>"}}, matching the website's error responses.
 */
public class HttpError extends RuntimeException {

    private final int status;

    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static HttpError badJson() {
        return new HttpError(400, "invalid json");
    }

    public static HttpError badBody() {
        return new HttpError(400, "invalid body");
    }

    public static HttpError unauthorized() {
        return new HttpError(401, "unauthorized");
    }

    public static HttpError notFound() {
        return new HttpError(404, "not found");
    }

    public static HttpError backendNotConfigured() {
        return new HttpError(503, "backend not configured");
    }
}
