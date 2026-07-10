package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Account {
    private final long accountNumber;
    private final String holderName;
    private final String pin;
    private final BigDecimal balance;
    private final AccountType accountType;
    private final AccountStatus status;
    private final LocalDateTime createdAt;

    public Account(long accountNumber, String holderName, String pin, BigDecimal balance,
                   AccountType accountType, AccountStatus status, LocalDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = balance;
        this.accountType = accountType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public long getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public String getPin() { return pin; }
    public BigDecimal getBalance() { return balance; }
    public AccountType getAccountType() { return accountType; }
    public AccountStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        return accountNumber == ((Account) o).accountNumber;
    }

    @Override
    public int hashCode() { return Objects.hash(accountNumber); }

    @Override
    public String toString() {
        return "Account{accountNumber=" + accountNumber + ", holderName='" + holderName
                + "', balance=" + balance + ", accountType=" + accountType
                + ", status=" + status + "}";
    }
}
