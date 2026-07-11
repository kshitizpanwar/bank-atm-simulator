package com.bank.service;

import com.bank.dao.AccountDao;
import com.bank.dao.AdminDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;

public class AuthService {

    private final UnitOfWork uow;
    private final AccountDao accountDao;
    private final AdminDao adminDao;
    private final PasswordHasher hasher;

    public AuthService(UnitOfWork uow, AccountDao accountDao, AdminDao adminDao, PasswordHasher hasher) {
        this.uow = uow;
        this.accountDao = accountDao;
        this.adminDao = adminDao;
        this.hasher = hasher;
    }

    public Account authenticateCustomer(long acct, String rawPin) {
        return uow.execute(c -> {
            Account account = accountDao.findByAccountNumber(c, acct).orElse(null);
            if (account == null || !hasher.verify(rawPin, account.getPin())) {
                throw new AuthenticationException("invalid account number or PIN");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("account is " + account.getStatus());
            }
            return account;
        });
    }

    public Admin authenticateAdmin(String username, String rawPassword) {
        return uow.execute(c -> {
            Admin admin = adminDao.findByUsername(c, username).orElse(null);
            if (admin == null || !hasher.verify(rawPassword, admin.getPasswordHash())) {
                throw new AuthenticationException("invalid username or password");
            }
            return admin;
        });
    }
}
