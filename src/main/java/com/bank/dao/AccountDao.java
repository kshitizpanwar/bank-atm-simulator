package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface AccountDao {
    void create(Connection c, Account account);
    Optional<Account> findByAccountNumber(Connection c, long accountNumber);
    List<Account> findAll(Connection c);
    void updateBalance(Connection c, long accountNumber, BigDecimal newBalance);
    void updatePin(Connection c, long accountNumber, String newPinHash);
    void updateStatus(Connection c, long accountNumber, AccountStatus status);
}
