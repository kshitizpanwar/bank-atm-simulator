package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.Admin;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();

    private final AccountDaoJdbc accountDao = new AccountDaoJdbc();
    private final AdminDaoJdbc adminDao = new AdminDaoJdbc();
    private final AccountService accountService =
            new AccountService(uow, accountDao, new TransactionDaoJdbc(), hasher);
    private final AuthService auth = new AuthService(uow, accountDao, adminDao, hasher);

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    private Account openActive() {
        return accountService.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("0.00"));
    }

    @Test
    void authenticateCustomerSucceedsWithCorrectPin() {
        Account a = openActive();
        Account result = auth.authenticateCustomer(a.getAccountNumber(), "1234");
        assertEquals(a.getAccountNumber(), result.getAccountNumber());
    }

    @Test
    void authenticateCustomerFailsWithWrongPin() {
        Account a = openActive();
        assertThrows(AuthenticationException.class,
                () -> auth.authenticateCustomer(a.getAccountNumber(), "0000"));
    }

    @Test
    void authenticateCustomerFailsForUnknownAccount() {
        assertThrows(AuthenticationException.class,
                () -> auth.authenticateCustomer(123L, "1234"));
    }

    @Test
    void authenticateCustomerRejectsBlockedAccount() {
        Account a = openActive();
        accountService.blockAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> auth.authenticateCustomer(a.getAccountNumber(), "1234"));
    }

    @Test
    void authenticateAdminSucceedsAndFails() {
        uow.executeVoid(c -> adminDao.create(c, new Admin(0L, "manager", hasher.hash("secret"))));
        assertEquals("manager", auth.authenticateAdmin("manager", "secret").getUsername());
        assertThrows(AuthenticationException.class, () -> auth.authenticateAdmin("manager", "wrong"));
        assertThrows(AuthenticationException.class, () -> auth.authenticateAdmin("ghost", "secret"));
    }
}
