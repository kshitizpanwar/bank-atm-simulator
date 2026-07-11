package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AccountLifecycleTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    private Account open() {
        return service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
    }

    @Test
    void changePinWithCorrectOldPinSucceeds() {
        Account a = open();
        service.changePin(a.getAccountNumber(), "1234", "5678");
        // new PIN works for a subsequent change; old PIN no longer does
        assertThrows(AuthenticationException.class,
                () -> service.changePin(a.getAccountNumber(), "1234", "0000"));
        service.changePin(a.getAccountNumber(), "5678", "0000"); // no throw
    }

    @Test
    void changePinRejectsWrongOldPin() {
        Account a = open();
        assertThrows(AuthenticationException.class,
                () -> service.changePin(a.getAccountNumber(), "0000", "5678"));
    }

    @Test
    void changePinRejectsBadNewPin() {
        Account a = open();
        assertThrows(InvalidPinException.class,
                () -> service.changePin(a.getAccountNumber(), "1234", "abc"));
    }

    @Test
    void blockedAccountCannotTransact() {
        Account a = open();
        service.blockAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> service.withdraw(a.getAccountNumber(), new BigDecimal("10.00")));
    }

    @Test
    void closedAccountCannotTransact() {
        Account a = open();
        service.closeAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> service.deposit(a.getAccountNumber(), new BigDecimal("10.00")));
    }

    @Test
    void blockedAccountCannotChangePin() {
        Account a = open();
        service.blockAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> service.changePin(a.getAccountNumber(), "1234", "5678"));
    }
}
