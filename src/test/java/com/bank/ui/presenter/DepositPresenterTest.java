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
import com.bank.ui.view.DepositView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DepositPresenterTest {

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

    static class FakeDepositView implements DepositView {
        String amount = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getAmount() { return amount; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Session sessionFor(String opening) {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal(opening));
        Session s = new Session();
        s.setAccount(a.getAccountNumber());
        return s;
    }

    @Test
    void validDepositIncreasesBalance() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "50.00";

        presenter.submit();

        assertNull(view.error);
        assertEquals(0, new BigDecimal("150.00").compareTo(accounts.getBalance(session.requireAccount())));
        assertNotNull(view.message);
    }

    @Test
    void nonPositiveAmountShowsError() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "0";

        presenter.submit();

        assertEquals("Enter a valid amount greater than zero.", view.error);
    }

    @Test
    void nonNumericAmountShowsValidationError() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "abc";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
