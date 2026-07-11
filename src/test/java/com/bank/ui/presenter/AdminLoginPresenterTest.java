package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AdminService;
import com.bank.service.AuthService;
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AdminLoginView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminLoginPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AdminService admins = new AdminService(uow, new AdminDaoJdbc(), hasher);
    private final AuthService auth = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
        admins.ensureDefaultAdmin();
    }

    static class FakeAdminLoginView implements AdminLoginView {
        String username = "", password = "", error;
        Runnable onLogin = () -> {}, onBack = () -> {};
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return password; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnLogin(Runnable h) { onLogin = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void validLoginSetsSessionAndShowsMenu() {
        FakeAdminLoginView view = new FakeAdminLoginView();
        AdminSession session = new AdminSession();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminLoginPresenter presenter = new AdminLoginPresenter(view, auth, nav, session);
        view.username = "admin";
        view.password = "admin123";

        presenter.login();

        assertTrue(nav.adminMenuShown);
        assertTrue(session.isLoggedIn());
        assertEquals("admin", session.requireAdmin());
        assertNull(view.error);
    }

    @Test
    void wrongPasswordShowsError() {
        FakeAdminLoginView view = new FakeAdminLoginView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminLoginPresenter presenter = new AdminLoginPresenter(view, auth, nav, new AdminSession());
        view.username = "admin";
        view.password = "wrong";

        presenter.login();

        assertFalse(nav.adminMenuShown);
        assertEquals("Invalid username or password.", view.error);
    }

    @Test
    void backReturnsToRoleSelect() {
        FakeAdminLoginView view = new FakeAdminLoginView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        new AdminLoginPresenter(view, auth, nav, new AdminSession());

        view.onBack.run();

        assertTrue(nav.roleSelectShown);
    }
}
