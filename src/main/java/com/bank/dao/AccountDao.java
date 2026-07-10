package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountDao {
    void create(Account account);
    Optional<Account> findByAccountNumber(long accountNumber);
    List<Account> findAll();
    void updateBalance(long accountNumber, BigDecimal newBalance);
    void updatePin(long accountNumber, String newPin);
    void updateStatus(long accountNumber, AccountStatus status);
}
