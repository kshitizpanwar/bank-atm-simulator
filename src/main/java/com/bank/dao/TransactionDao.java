package com.bank.dao;

import com.bank.model.Transaction;

import java.util.List;

public interface TransactionDao {
    void insert(Transaction transaction);
    List<Transaction> findByAccountNumber(long accountNumber);
    List<Transaction> findRecent(long accountNumber, int limit);
}
