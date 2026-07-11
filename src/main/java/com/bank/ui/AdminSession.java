package com.bank.ui;

public class AdminSession {
    private String username;
    private Long selectedAccount;

    public void setAdmin(String username) {
        this.username = username;
    }

    public void clear() {
        this.username = null;
        this.selectedAccount = null;
    }

    public boolean isLoggedIn() {
        return username != null;
    }

    public String requireAdmin() {
        if (username == null) {
            throw new IllegalStateException("no logged-in admin");
        }
        return username;
    }

    public void selectAccount(long accountNumber) {
        this.selectedAccount = accountNumber;
    }

    public long requireSelectedAccount() {
        if (selectedAccount == null) {
            throw new IllegalStateException("no account selected");
        }
        return selectedAccount;
    }
}
