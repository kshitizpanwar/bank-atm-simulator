package com.bank.service;

public class InvalidPinException extends BankServiceException {
    public InvalidPinException(String message) {
        super(message);
    }
}
