package com.bank.service;

public class AccountNotFoundException extends BankServiceException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
