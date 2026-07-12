package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.ui.view.AdminHomeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminHomePresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService accounts =
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

    static class FakeAdminHomeView implements AdminHomeView {
        String summary;
        @Override public void showSummary(String s) { summary = s; }
    }

    @Test
    void summaryCountsAccountsByStatus() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00")); // ACTIVE
        Account b = accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        accounts.blockAccount(b.getAccountNumber());
        Account c = accounts.openAccount("Cara", "9012", AccountType.SAVINGS, new BigDecimal("0.00"));
        accounts.closeAccount(c.getAccountNumber());

        FakeAdminHomeView view = new FakeAdminHomeView();
        new AdminHomePresenter(view, accounts).load();

        assertNotNull(view.summary);
        assertTrue(view.summary.contains("Total accounts: 3"), view.summary);
        assertTrue(view.summary.contains("Active: 1"), view.summary);
        assertTrue(view.summary.contains("Blocked: 1"), view.summary);
        assertTrue(view.summary.contains("Closed: 1"), view.summary);
    }
}
