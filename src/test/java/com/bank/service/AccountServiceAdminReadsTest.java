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

class AccountServiceAdminReadsTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

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

    @Test
    void listAllAccountsReturnsEveryAccount() {
        service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        service.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        assertEquals(2, service.listAllAccounts().size());
    }

    @Test
    void getAccountReturnsItOrThrows() {
        Account a = service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("10.00"));
        assertEquals("Asha", service.getAccount(a.getAccountNumber()).getHolderName());
        assertThrows(AccountNotFoundException.class, () -> service.getAccount(1L));
    }

    @Test
    void accountHistoryReturnsTransactionsOrThrows() {
        Account a = service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        service.deposit(a.getAccountNumber(), new BigDecimal("20.00"));
        service.withdraw(a.getAccountNumber(), new BigDecimal("5.00"));
        assertEquals(3, service.accountHistory(a.getAccountNumber()).size());
        assertThrows(AccountNotFoundException.class, () -> service.accountHistory(1L));
    }

    @Test
    void deleteAccountRemovesAccountAndItsTransactions() {
        Account a = service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        service.deposit(a.getAccountNumber(), new BigDecimal("10.00"));

        service.deleteAccount(a.getAccountNumber());

        assertThrows(AccountNotFoundException.class, () -> service.getAccount(a.getAccountNumber()));
        long txCount = uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery(
                         "SELECT COUNT(*) FROM transactions WHERE account_number=" + a.getAccountNumber())) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw new DaoException("count", e);
            }
        });
        assertEquals(0, txCount);
    }

    @Test
    void deleteAccountUnknownThrows() {
        assertThrows(AccountNotFoundException.class, () -> service.deleteAccount(1L));
    }
}
