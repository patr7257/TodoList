package dk.dtu.model;

import java.io.Serializable;

public final class User implements Serializable {
    private final String username;

    public User(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}