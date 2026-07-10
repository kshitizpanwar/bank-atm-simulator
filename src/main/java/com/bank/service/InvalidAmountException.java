package com.bank.service;

public class InvalidAmountException extends BankServiceException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
