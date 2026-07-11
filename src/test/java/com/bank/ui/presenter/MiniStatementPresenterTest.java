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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.MiniStatementView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiniStatementPresenterTest {

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

    static class FakeMiniStatementView implements MiniStatementView {
        List<String> lines;
        String error;
        Runnable onBack = () -> {};
        @Override public void showTransactions(List<String> l) { lines = l; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void loadRendersRecentTransactions() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.deposit(a.getAccountNumber(), new BigDecimal("20.00"));
        accounts.withdraw(a.getAccountNumber(), new BigDecimal("5.00"));
        Session session = new Session();
        session.setAccount(a.getAccountNumber());
        FakeMiniStatementView view = new FakeMiniStatementView();
        MiniStatementPresenter presenter = new MiniStatementPresenter(view, accounts, session, new FakeNavigator());

        presenter.load();

        assertNull(view.error);
        assertNotNull(view.lines);
        // opening deposit + deposit + withdraw = 3 transactions
        assertEquals(3, view.lines.size());
    }

    @Test
    void unknownAccountShowsError() {
        Session session = new Session();
        session.setAccount(1L); // never created
        FakeMiniStatementView view = new FakeMiniStatementView();
        MiniStatementPresenter presenter = new MiniStatementPresenter(view, accounts, session, new FakeNavigator());

        presenter.load();

        assertEquals("Account not found.", view.error);
        assertNull(view.lines);
    }
}
