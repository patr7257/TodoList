package dk.dtu.api.auth;

import java.util.Optional;

import dk.dtu.api.domain.TodoService;
import dk.dtu.api.domain.UserRow;

/**
 * Login: look up a user by (trimmed, lower-cased) email, verify the password
 * against the stored scrypt hash, and issue a session token. A missing user and
 * a wrong password fail identically, matching the website's uniform 401.
 */
public final class AuthService {

    private final TodoService todo;
    private final Token token;

    public AuthService(TodoService todo, Token token) {
        this.todo = todo;
        this.token = token;
    }

    public record LoginResult(UserRow user, String token) {
    }

    /**
     * Attempts a login. Returns empty on bad credentials (caller answers 401).
     * The token inside may still be null when no session secret is configured;
     * the caller treats a null token as "backend not configured" (503).
     */
    public Optional<LoginResult> login(String email, String password) {
        String normalized = email.trim().toLowerCase();
        Optional<UserRow> found = todo.findUserByEmail(normalized);
        if (found.isEmpty() || !Scrypt.verify(password, found.get().pwHash())) {
            return Optional.empty();
        }
        UserRow user = found.get();
        return Optional.of(new LoginResult(user, token.issue(user.id())));
    }

    public Token token() {
        return token;
    }
}
