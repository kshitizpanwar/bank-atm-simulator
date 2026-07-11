package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountNotFoundException;
import com.bank.service.AccountService;
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.ManageAccountView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManageAccountPresenterTest {

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

    static class FakeManageAccountView implements ManageAccountView {
        String details, message, error, confirmMessage;
        List<String> history;
        boolean autoConfirm = true;
        Runnable onBlock = () -> {}, onClose = () -> {}, onDelete = () -> {}, onBack = () -> {};
        @Override public void showDetails(String d) { details = d; }
        @Override public void showHistory(List<String> h) { history = h; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBlock(Runnable h) { onBlock = h; }
        @Override public void setOnClose(Runnable h) { onClose = h; }
        @Override public void setOnDelete(Runnable h) { onDelete = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
        @Override public void confirmDelete(String message, Runnable onConfirmed) {
            confirmMessage = message;
            if (autoConfirm) onConfirmed.run();
        }
    }

    private AdminSession sessionFor(long acct) {
        AdminSession s = new AdminSession();
        s.selectAccount(acct);
        return s;
    }

    @Test
    void loadShowsDetailsAndHistory() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.deposit(a.getAccountNumber(), new BigDecimal("10.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.load();

        assertNull(view.error);
        assertNotNull(view.details);
        assertTrue(view.details.contains(String.valueOf(a.getAccountNumber())));
        assertEquals(2, view.history.size()); // opening deposit + deposit
    }

    @Test
    void blockSetsStatusBlocked() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.block();

        assertEquals(AccountStatus.BLOCKED, accounts.getAccount(a.getAccountNumber()).getStatus());
        assertNotNull(view.details);
    }

    @Test
    void closeSetsStatusClosed() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.close();

        assertEquals(AccountStatus.CLOSED, accounts.getAccount(a.getAccountNumber()).getStatus());
        assertNotNull(view.details);
    }

    @Test
    void deleteConfirmedRemovesAccountAndReturnsToList() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), nav);

        presenter.delete();

        assertNotNull(view.confirmMessage);
        assertThrows(AccountNotFoundException.class, () -> accounts.getAccount(a.getAccountNumber()));
        assertTrue(nav.allAccountsShown);
    }

    @Test
    void deleteCancelledKeepsAccount() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        view.autoConfirm = false;
        FakeAdminNavigator nav = new FakeAdminNavigator();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), nav);

        presenter.delete();

        assertEquals(a.getAccountNumber(), accounts.getAccount(a.getAccountNumber()).getAccountNumber());
        assertFalse(nav.allAccountsShown);
    }
}
