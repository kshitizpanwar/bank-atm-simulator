package com.bank.ui;

public class Session {
    private Long accountNumber;

    public void setAccount(long accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void clear() {
        this.accountNumber = null;
    }

    public boolean isLoggedIn() {
        return accountNumber != null;
    }

    public long requireAccount() {
        if (accountNumber == null) {
            throw new IllegalStateException("no logged-in account");
        }
        return accountNumber;
    }
}
