package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.ChangePinView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ChangePinPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
    private final AuthService auth =
            new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

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

    static class FakeChangePinView implements ChangePinView {
        String oldPin = "", newPin = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getOldPin() { return oldPin; }
        @Override public String getNewPin() { return newPin; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Session login(String pin) {
        Account a = accounts.openAccount("Asha", pin, AccountType.SAVINGS, new BigDecimal("0.00"));
        Session s = new Session();
        s.setAccount(a.getAccountNumber());
        return s;
    }

    @Test
    void validChangeUpdatesPin() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "1234";
        view.newPin = "5678";

        presenter.submit();

        assertNull(view.error);
        // new PIN now authenticates
        assertNotNull(auth.authenticateCustomer(session.requireAccount(), "5678"));
    }

    @Test
    void wrongOldPinShowsError() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "0000";
        view.newPin = "5678";

        presenter.submit();

        assertEquals("Invalid account number or PIN.", view.error);
    }

    @Test
    void badNewPinShowsError() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "1234";
        view.newPin = "12";

        presenter.submit();

        assertEquals("PIN must be exactly 4 digits.", view.error);
    }
}
