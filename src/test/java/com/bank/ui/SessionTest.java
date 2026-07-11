package com.bank.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    @Test
    void startsLoggedOut() {
        Session s = new Session();
        assertFalse(s.isLoggedIn());
        assertThrows(IllegalStateException.class, s::requireAccount);
    }

    @Test
    void setAndClearAccount() {
        Session s = new Session();
        s.setAccount(1234567890L);
        assertTrue(s.isLoggedIn());
        assertEquals(1234567890L, s.requireAccount());
        s.clear();
        assertFalse(s.isLoggedIn());
    }
}
