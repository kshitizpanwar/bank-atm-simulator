package com.bank.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    public String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt());
    }

    public boolean verify(String raw, String hash) {
        return BCrypt.checkpw(raw, hash);
    }
}
