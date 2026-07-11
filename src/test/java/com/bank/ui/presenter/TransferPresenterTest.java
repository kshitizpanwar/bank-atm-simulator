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
import com.bank.ui.view.TransferView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class TransferPresenterTest {

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

    static class FakeTransferView implements TransferView {
        String target = "", amount = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getTargetAccount() { return target; }
        @Override public String getAmount() { return amount; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void validTransferMovesMoney() {
        Account from = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        Account to = accounts.openAccount("Ben", "5678", AccountType.SAVINGS, new BigDecimal("0.00"));
        Session session = new Session();
        session.setAccount(from.getAccountNumber());
        FakeTransferView view = new FakeTransferView();
        TransferPresenter presenter = new TransferPresenter(view, accounts, session, new FakeNavigator());
        view.target = String.valueOf(to.getAccountNumber());
        view.amount = "40.00";

        presenter.submit();

        assertNull(view.error);
        assertEquals(0, new BigDecimal("60.00").compareTo(accounts.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("40.00").compareTo(accounts.getBalance(to.getAccountNumber())));
    }

    @Test
    void nonNumericTargetShowsValidationError() {
        Account from = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        Session session = new Session();
        session.setAccount(from.getAccountNumber());
        FakeTransferView view = new FakeTransferView();
        TransferPresenter presenter = new TransferPresenter(view, accounts, session, new FakeNavigator());
        view.target = "xyz";
        view.amount = "40.00";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
