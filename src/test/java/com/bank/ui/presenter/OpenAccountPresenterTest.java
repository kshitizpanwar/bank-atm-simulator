package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.ui.FakeNavigator;
import com.bank.ui.view.OpenAccountView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class OpenAccountPresenterTest {

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

    static class FakeOpenAccountView implements OpenAccountView {
        String name = "", type = "SAVINGS", pin = "", opening = "", error, message;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getHolderName() { return name; }
        @Override public String getAccountType() { return type; }
        @Override public String getPin() { return pin; }
        @Override public String getOpeningBalance() { return opening; }
        @Override public void showError(String m) { error = m; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void validSubmitCreatesAccountAndShowsNumber() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.type = "SAVINGS";
        view.pin = "1234";
        view.opening = "500.00";

        presenter.submit();

        assertNull(view.error);
        assertNotNull(view.message, "should show the new account number");
        long count = uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) { throw new DaoException("count", e); }
        });
        assertEquals(1, count);
    }

    @Test
    void badPinShowsErrorAndCreatesNothing() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.pin = "12";
        view.opening = "500.00";

        presenter.submit();

        assertEquals("PIN must be exactly 4 digits.", view.error);
    }

    @Test
    void nonNumericOpeningBalanceShowsValidationError() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.pin = "1234";
        view.opening = "abc";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
