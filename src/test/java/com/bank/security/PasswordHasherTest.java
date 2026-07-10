package com.bank.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsNotThePlaintextAndVerifies() {
        String hash = hasher.hash("1234");
        assertNotEquals("1234", hash);
        assertTrue(hash.startsWith("$2"), "expected a BCrypt hash");
        assertTrue(hasher.verify("1234", hash));
    }

    @Test
    void verifyFailsForWrongInput() {
        String hash = hasher.hash("1234");
        assertFalse(hasher.verify("9999", hash));
    }

    @Test
    void sameInputProducesDifferentHashes() {
        assertNotEquals(hasher.hash("1234"), hasher.hash("1234"));
    }
}
