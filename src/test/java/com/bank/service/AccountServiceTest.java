package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

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

    private Account open(String balance) {
        return service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal(balance));
    }

    @Test
    void openAccountGeneratesNumberHashesPinAndSetsBalance() {
        Account a = open("100.00");
        assertTrue(a.getAccountNumber() >= 1_000_000_000L);
        assertNotEquals("1234", a.getPin(), "PIN must be hashed, not plaintext");
        assertEquals(0, new BigDecimal("100.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void openAccountRejectsBadPin() {
        assertThrows(InvalidPinException.class,
                () -> service.openAccount("X", "12", AccountType.SAVINGS, BigDecimal.ZERO));
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        Account a = open("50.00");
        service.deposit(a.getAccountNumber(), new BigDecimal("25.50"));
        assertEquals(0, new BigDecimal("75.50").compareTo(service.getBalance(a.getAccountNumber())));
        List<Transaction> tx = service.miniStatement(a.getAccountNumber(), 10);
        assertFalse(tx.isEmpty());
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        Account a = open("50.00");
        assertThrows(InvalidAmountException.class,
                () -> service.deposit(a.getAccountNumber(), new BigDecimal("0.00")));
    }

    @Test
    void withdrawDecreasesBalance() {
        Account a = open("50.00");
        service.withdraw(a.getAccountNumber(), new BigDecimal("20.00"));
        assertEquals(0, new BigDecimal("30.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void withdrawOverBalanceIsRejected() {
        Account a = open("50.00");
        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(a.getAccountNumber(), new BigDecimal("50.01")));
        assertEquals(0, new BigDecimal("50.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void getBalanceUnknownAccountThrows() {
        assertThrows(AccountNotFoundException.class, () -> service.getBalance(1L));
    }

    @Test
    void transferMovesMoneyAtomically() {
        Account from = open("100.00");
        Account to = open("0.00");
        service.transfer(from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("60.00").compareTo(service.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("40.00").compareTo(service.getBalance(to.getAccountNumber())));
    }

    @Test
    void transferWithInsufficientFundsRollsBackBothBalances() {
        Account from = open("30.00");
        Account to = open("10.00");
        assertThrows(InsufficientFundsException.class,
                () -> service.transfer(from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("40.00")));
        assertEquals(0, new BigDecimal("30.00").compareTo(service.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("10.00").compareTo(service.getBalance(to.getAccountNumber())));
    }
}
