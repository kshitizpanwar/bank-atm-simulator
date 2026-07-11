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
import com.bank.ui.view.LoginView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class LoginPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
    private final AuthService auth =
            new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

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

    /** Minimal fake view returning canned inputs and recording the last error. */
    static class FakeLoginView implements LoginView {
        String account = "", pin = "", error;
        Runnable onLogin = () -> {}, onOpen = () -> {}, onBack = () -> {};
        @Override public String getAccountNumber() { return account; }
        @Override public String getPin() { return pin; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnLogin(Runnable h) { onLogin = h; }
        @Override public void setOnOpenAccount(Runnable h) { onOpen = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Account openAccount() {
        return accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
    }

    @Test
    void validLoginNavigatesToMenuAndSetsSession() {
        Account a = openAccount();
        FakeLoginView view = new FakeLoginView();
        Session session = new Session();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, session);

        view.account = String.valueOf(a.getAccountNumber());
        view.pin = "1234";
        presenter.login();

        assertTrue(nav.menuShown);
        assertTrue(session.isLoggedIn());
        assertEquals(a.getAccountNumber(), session.requireAccount());
        assertNull(view.error);
    }

    @Test
    void wrongPinShowsErrorAndDoesNotNavigate() {
        Account a = openAccount();
        FakeLoginView view = new FakeLoginView();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, new Session());

        view.account = String.valueOf(a.getAccountNumber());
        view.pin = "0000";
        presenter.login();

        assertFalse(nav.menuShown);
        assertEquals("Invalid account number or PIN.", view.error);
    }

    @Test
    void nonNumericAccountShowsValidationError() {
        FakeLoginView view = new FakeLoginView();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, new Session());

        view.account = "abc";
        view.pin = "1234";
        presenter.login();

        assertEquals("Enter a valid number.", view.error);
        assertFalse(nav.menuShown);
    }

    @Test
    void backReturnsToRoleSelect() {
        FakeLoginView view = new FakeLoginView();
        FakeNavigator nav = new FakeNavigator();
        new LoginPresenter(view, auth, nav, new Session());

        view.onBack.run();

        assertTrue(nav.roleSelectShown);
    }
}
