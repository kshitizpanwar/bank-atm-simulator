package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountDao dao = new AccountDaoJdbc();

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanTables() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    private Account sample(long number) {
        return new Account(number, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    @Test
    void createThenFindRoundTrips() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));

        Optional<Account> found = uow.execute(c -> dao.findByAccountNumber(c, 1000000001L));
        assertTrue(found.isPresent());
        Account a = found.get();
        assertEquals("Asha", a.getHolderName());
        assertEquals("1234", a.getPin());
        assertEquals(0, new BigDecimal("100.00").compareTo(a.getBalance()));
        assertEquals(AccountType.SAVINGS, a.getAccountType());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());
        assertNotNull(a.getCreatedAt());
    }

    @Test
    void findByAccountNumberReturnsEmptyWhenAbsent() {
        assertTrue(uow.execute(c -> dao.findByAccountNumber(c, 9999999999L)).isEmpty());
    }

    @Test
    void findAllReturnsEveryAccount() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.create(c, sample(1000000002L)));
        List<Account> all = uow.execute(dao::findAll);
        assertEquals(2, all.size());
    }

    @Test
    void updateBalancePersistsExactValue() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updateBalance(c, 1000000001L, new BigDecimal("250.75")));
        BigDecimal balance = uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getBalance());
        assertEquals(0, new BigDecimal("250.75").compareTo(balance));
    }

    @Test
    void updatePinPersists() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updatePin(c, 1000000001L, "4321"));
        assertEquals("4321", uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getPin()));
    }

    @Test
    void updateStatusPersists() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updateStatus(c, 1000000001L, AccountStatus.BLOCKED));
        assertEquals(AccountStatus.BLOCKED, uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getStatus()));
    }
}
