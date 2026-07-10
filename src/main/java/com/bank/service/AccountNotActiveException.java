package com.bank.service;

public class AccountNotActiveException extends BankServiceException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
