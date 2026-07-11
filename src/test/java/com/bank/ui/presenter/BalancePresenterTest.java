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
import com.bank.ui.view.BalanceView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class BalancePresenterTest {

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

    static class FakeBalanceView implements BalanceView {
        String balance, error;
        Runnable onBack = () -> {};
        @Override public void showBalance(String b) { balance = b; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void loadShowsCurrentBalance() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("250.00"));
        Session session = new Session();
        session.setAccount(a.getAccountNumber());
        FakeBalanceView view = new FakeBalanceView();
        BalancePresenter presenter = new BalancePresenter(view, accounts, session, new FakeNavigator());

        presenter.load();

        assertNotNull(view.balance);
        assertTrue(view.balance.contains("250.00"), "balance text should contain the amount");
        assertNull(view.error);
    }
}
