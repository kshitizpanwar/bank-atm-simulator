package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private final long id;
    private final long accountNumber;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime timestamp;

    public Transaction(long id, long accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, LocalDateTime timestamp) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public long getAccountNumber() { return accountNumber; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", accountNumber=" + accountNumber
                + ", type=" + type + ", amount=" + amount
                + ", balanceAfter=" + balanceAfter + ", timestamp=" + timestamp + "}";
    }
}
