package com.bank.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {
    @Test
    void accountTypeRoundTripAndLength() {
        assertEquals(2, AccountType.values().length);
        assertEquals(AccountType.SAVINGS, AccountType.valueOf("SAVINGS"));
        assertEquals(AccountType.CURRENT, AccountType.valueOf("CURRENT"));
    }

    @Test
    void accountStatusRoundTripAndLength() {
        assertEquals(3, AccountStatus.values().length);
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        assertEquals(AccountStatus.BLOCKED, AccountStatus.valueOf("BLOCKED"));
        assertEquals(AccountStatus.CLOSED, AccountStatus.valueOf("CLOSED"));
    }

    @Test
    void transactionTypeRoundTripAndLength() {
        assertEquals(3, TransactionType.values().length);
        assertEquals(TransactionType.DEPOSIT, TransactionType.valueOf("DEPOSIT"));
        assertEquals(TransactionType.WITHDRAW, TransactionType.valueOf("WITHDRAW"));
        assertEquals(TransactionType.TRANSFER, TransactionType.valueOf("TRANSFER"));
    }
}
