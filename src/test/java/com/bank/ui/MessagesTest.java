package com.bank.ui;

import com.bank.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test
    void mapsEachDomainException() {
        assertEquals("Invalid account number or PIN.", Messages.of(new AuthenticationException("x")));
        assertEquals("This account is blocked or closed.", Messages.of(new AccountNotActiveException("x")));
        assertEquals("Insufficient funds.", Messages.of(new InsufficientFundsException("x")));
        assertEquals("Enter a valid amount greater than zero.", Messages.of(new InvalidAmountException("x")));
        assertEquals("PIN must be exactly 4 digits.", Messages.of(new InvalidPinException("x")));
        assertEquals("Account not found.", Messages.of(new AccountNotFoundException("x")));
    }

    @Test
    void unknownSubtypeFallsBackToGeneric() {
        assertEquals("Something went wrong. Please try again.",
                Messages.of(new BankServiceException("x")));
    }
}
