package com.bank.service;

public class InsufficientFundsException extends BankServiceException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
