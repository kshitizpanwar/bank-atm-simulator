package com.bank.ui;

import com.bank.service.AccountNotActiveException;
import com.bank.service.AccountNotFoundException;
import com.bank.service.AuthenticationException;
import com.bank.service.BankServiceException;
import com.bank.service.InsufficientFundsException;
import com.bank.service.InvalidAmountException;
import com.bank.service.InvalidPinException;

public final class Messages {

    private Messages() { }

    public static String of(BankServiceException e) {
        if (e instanceof AuthenticationException) {
            return "Invalid account number or PIN.";
        }
        if (e instanceof AccountNotActiveException) {
            return "This account is blocked or closed.";
        }
        if (e instanceof InsufficientFundsException) {
            return "Insufficient funds.";
        }
        if (e instanceof InvalidAmountException) {
            return "Enter a valid amount greater than zero.";
        }
        if (e instanceof InvalidPinException) {
            return "PIN must be exactly 4 digits.";
        }
        if (e instanceof AccountNotFoundException) {
            return "Account not found.";
        }
        return "Something went wrong. Please try again.";
    }
}
