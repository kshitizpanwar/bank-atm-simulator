package com.bank.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {
    @Test
    void enumsRoundTripThroughName() {
        assertEquals(AccountType.SAVINGS, AccountType.valueOf("SAVINGS"));
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        assertEquals(TransactionType.DEPOSIT, TransactionType.valueOf("DEPOSIT"));
    }
}
