package com.bank.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {
    @Test
    void allSubtypesAreBankServiceExceptions() {
        assertTrue(new AccountNotFoundException("x") instanceof BankServiceException);
        assertTrue(new AccountNotActiveException("x") instanceof BankServiceException);
        assertTrue(new InsufficientFundsException("x") instanceof BankServiceException);
        assertTrue(new InvalidAmountException("x") instanceof BankServiceException);
        assertTrue(new InvalidPinException("x") instanceof BankServiceException);
        assertTrue(new AuthenticationException("x") instanceof BankServiceException);
        assertTrue(new BankServiceException("x") instanceof RuntimeException);
    }

    @Test
    void messageIsPreserved() {
        assertEquals("nope", new InsufficientFundsException("nope").getMessage());
    }
}
