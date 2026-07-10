package com.bank.service;

public class AuthenticationException extends BankServiceException {
    public AuthenticationException(String message) {
        super(message);
    }
}
