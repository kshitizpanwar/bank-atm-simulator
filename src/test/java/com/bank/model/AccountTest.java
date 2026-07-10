package com.bank.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    @Test
    void holdsValuesAndComparesByAccountNumber() {
        LocalDateTime now = LocalDateTime.now();
        Account a = new Account(1234567890L, "Asha", "1234",
                new BigDecimal("100.00"), AccountType.SAVINGS, AccountStatus.ACTIVE, now);

        assertEquals(1234567890L, a.getAccountNumber());
        assertEquals("Asha", a.getHolderName());
        assertEquals(new BigDecimal("100.00"), a.getBalance());
        assertEquals(AccountType.SAVINGS, a.getAccountType());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());

        Account same = new Account(1234567890L, "Different", "9999",
                BigDecimal.ZERO, AccountType.CURRENT, AccountStatus.BLOCKED, now);
        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
    }
}
