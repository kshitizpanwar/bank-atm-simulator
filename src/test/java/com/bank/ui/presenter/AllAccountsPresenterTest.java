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
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AccountRow;
import com.bank.ui.view.AllAccountsView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AllAccountsPresenterTest {

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

    static class FakeAllAccountsView implements AllAccountsView {
        List<AccountRow> rows;
        String error;
        AccountRow selected;
        Consumer<String> onSearch = q -> {};
        Runnable onManage = () -> {};
        @Override public void showAccounts(List<AccountRow> r) { rows = r; }
        @Override public void showError(String m) { error = m; }
        @Override public AccountRow getSelected() { return selected; }
        @Override public void setOnSearch(Consumer<String> h) { onSearch = h; }
        @Override public void setOnManage(Runnable h) { onManage = h; }
    }

    @Test
    void loadRendersOneStructuredRowPerAccount() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());

        presenter.load();

        assertNull(view.error);
        assertEquals(2, view.rows.size());
        AccountRow r = view.rows.stream().filter(x -> x.holderName().equals("Asha")).findFirst().orElseThrow();
        assertEquals("SAVINGS", r.accountType());
        assertEquals("ACTIVE", r.status());
    }

    @Test
    void searchFiltersByHolderName() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());

        presenter.search("as");

        assertEquals(1, view.rows.size());
        assertEquals("Asha", view.rows.get(0).holderName());
    }

    @Test
    void manageSelectedNavigatesAndStoresSelection() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AdminSession session = new AdminSession();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accounts, session, nav);
        view.selected = new AccountRow(a.getAccountNumber(), "Asha", "SAVINGS", "100.00", "ACTIVE");

        presenter.manage();

        assertEquals(a.getAccountNumber(), session.requireSelectedAccount());
        assertEquals(a.getAccountNumber(), nav.manageAccountShownFor);
    }

    @Test
    void manageWithNoSelectionShowsError() {
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());
        view.selected = null;

        presenter.manage();

        assertEquals("Select an account first.", view.error);
    }
}
