package com.bank.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminSessionTest {
    @Test
    void adminLifecycle() {
        AdminSession s = new AdminSession();
        assertFalse(s.isLoggedIn());
        assertThrows(IllegalStateException.class, s::requireAdmin);
        s.setAdmin("manager");
        assertTrue(s.isLoggedIn());
        assertEquals("manager", s.requireAdmin());
        s.clear();
        assertFalse(s.isLoggedIn());
    }

    @Test
    void selectedAccount() {
        AdminSession s = new AdminSession();
        assertThrows(IllegalStateException.class, s::requireSelectedAccount);
        s.selectAccount(1000000001L);
        assertEquals(1000000001L, s.requireSelectedAccount());
        s.clear();
        assertThrows(IllegalStateException.class, s::requireSelectedAccount);
    }
}
