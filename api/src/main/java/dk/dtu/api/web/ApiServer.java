package dk.dtu.api.web;

import java.util.LinkedHashMap;
import java.util.Map;

import dk.dtu.api.auth.AuthFilter;

import io.javalin.Javalin;

/**
 * Builds the Javalin application: JSON mapper, auth before-filter, the mirrored
 * /api/todo/* routes, and the error mappers that turn {@link HttpError} into
 * {@code {"error": "..."}} bodies with the right status.
 */
public final class ApiServer {

    private ApiServer() {
    }

    public static Javalin create(Backend backend) {
        AuthController auth = new AuthController(backend);
        StateController state = new StateController(backend);
        ListsController lists = new ListsController(backend);
        ItemsController items = new ItemsController(backend);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new GsonJsonMapper());
            config.showJavalinBanner = false;
        });

        app.before(new AuthFilter(backend));

        app.post("/api/todo/login", auth::login);
        app.post("/api/todo/logout", auth::logout);
        app.get("/api/todo/state", state::get);
        app.post("/api/todo/lists", lists::create);
        app.patch("/api/todo/lists/{id}", lists::update);
        app.delete("/api/todo/lists/{id}", lists::delete);
        app.post("/api/todo/items", items::create);
        app.patch("/api/todo/items/{id}", items::update);
        app.delete("/api/todo/items/{id}", items::delete);

        app.exception(HttpError.class, (e, ctx) -> ctx.status(e.status()).json(error(e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).json(error("internal error"));
        });

        return app;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
