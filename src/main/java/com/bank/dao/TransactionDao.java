package com.bank.dao;

import com.bank.model.Transaction;

import java.sql.Connection;
import java.util.List;

public interface TransactionDao {
    void insert(Connection c, Transaction transaction);
    List<Transaction> findByAccountNumber(Connection c, long accountNumber);
    List<Transaction> findRecent(Connection c, long accountNumber, int limit);
    void deleteByAccountNumber(Connection c, long accountNumber);
}
