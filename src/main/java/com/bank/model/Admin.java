package com.bank.model;

public class Admin {
    private final long id;
    private final String username;
    private final String passwordHash;

    public Admin(long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }

    @Override
    public String toString() {
        return "Admin{id=" + id + ", username='" + username + "'}";
    }
}
