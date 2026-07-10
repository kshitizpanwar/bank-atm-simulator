package com.bank.service;

import com.bank.dao.AccountDao;
import com.bank.dao.TransactionDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.security.PasswordHasher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public class AccountService {

    private final UnitOfWork uow;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final PasswordHasher hasher;
    private final Random random = new Random();

    public AccountService(UnitOfWork uow, AccountDao accountDao,
                          TransactionDao transactionDao, PasswordHasher hasher) {
        this.uow = uow;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.hasher = hasher;
    }

    public Account openAccount(String holderName, String rawPin, AccountType type, BigDecimal openingBalance) {
        requireValidPin(rawPin);
        if (openingBalance == null || openingBalance.signum() < 0) {
            throw new InvalidAmountException("opening balance must be >= 0");
        }
        BigDecimal opening = scale(openingBalance);
        return uow.execute(c -> {
            long number = generateUniqueAccountNumber(c);
            Account account = new Account(number, holderName, hasher.hash(rawPin), opening,
                    type, AccountStatus.ACTIVE, LocalDateTime.now());
            accountDao.create(c, account);
            if (opening.signum() > 0) {
                transactionDao.insert(c, new Transaction(0L, number, TransactionType.DEPOSIT,
                        opening, opening, LocalDateTime.now()));
            }
            return account;
        });
    }

    public BigDecimal getBalance(long acct) {
        return uow.execute(c -> loadOrThrow(c, acct).getBalance());
    }

    public void deposit(long acct, BigDecimal amount) {
        requirePositive(amount);
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account a = loadOrThrow(c, acct);
            requireActive(a);
            BigDecimal newBalance = scale(a.getBalance().add(amt));
            accountDao.updateBalance(c, acct, newBalance);
            transactionDao.insert(c, new Transaction(0L, acct, TransactionType.DEPOSIT,
                    amt, newBalance, LocalDateTime.now()));
        });
    }

    public void withdraw(long acct, BigDecimal amount) {
        requirePositive(amount);
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account a = loadOrThrow(c, acct);
            requireActive(a);
            if (a.getBalance().compareTo(amt) < 0) {
                throw new InsufficientFundsException("insufficient funds");
            }
            BigDecimal newBalance = scale(a.getBalance().subtract(amt));
            accountDao.updateBalance(c, acct, newBalance);
            transactionDao.insert(c, new Transaction(0L, acct, TransactionType.WITHDRAW,
                    amt, newBalance, LocalDateTime.now()));
        });
    }

    public void transfer(long from, long to, BigDecimal amount) {
        requirePositive(amount);
        if (from == to) {
            throw new InvalidAmountException("cannot transfer to the same account");
        }
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account src = loadOrThrow(c, from);
            Account dst = loadOrThrow(c, to);
            requireActive(src);
            requireActive(dst);
            if (src.getBalance().compareTo(amt) < 0) {
                throw new InsufficientFundsException("insufficient funds");
            }
            BigDecimal newSrc = scale(src.getBalance().subtract(amt));
            BigDecimal newDst = scale(dst.getBalance().add(amt));
            accountDao.updateBalance(c, from, newSrc);
            accountDao.updateBalance(c, to, newDst);
            transactionDao.insert(c, new Transaction(0L, from, TransactionType.TRANSFER,
                    amt, newSrc, LocalDateTime.now()));
            transactionDao.insert(c, new Transaction(0L, to, TransactionType.TRANSFER,
                    amt, newDst, LocalDateTime.now()));
        });
    }

    public List<Transaction> miniStatement(long acct, int n) {
        return uow.execute(c -> {
            loadOrThrow(c, acct);
            return transactionDao.findRecent(c, acct, n);
        });
    }

    // ---- helpers (shared with lifecycle/PIN operations) ----

    Account loadOrThrow(Connection c, long acct) {
        return accountDao.findByAccountNumber(c, acct)
                .orElseThrow(() -> new AccountNotFoundException("no account " + acct));
    }

    void requireActive(Account a) {
        if (a.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("account " + a.getAccountNumber() + " is " + a.getStatus());
        }
    }

    void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("amount must be > 0");
        }
    }

    void requireValidPin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new InvalidPinException("PIN must be exactly 4 digits");
        }
    }

    BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private long generateUniqueAccountNumber(Connection c) {
        long number;
        do {
            number = 1_000_000_000L + (long) (random.nextDouble() * 9_000_000_000L);
        } while (accountDao.findByAccountNumber(c, number).isPresent());
        return number;
    }
}
