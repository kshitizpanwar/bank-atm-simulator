package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private final AccountDao dao = new AccountDaoJdbc(db);

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM accounts");
        }
    }

    private Account sample(long number) {
        return new Account(number, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    @Test
    void createThenFindRoundTrips() {
        dao.create(sample(1000000001L));

        Optional<Account> found = dao.findByAccountNumber(1000000001L);
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
        assertTrue(dao.findByAccountNumber(9999999999L).isEmpty());
    }

    @Test
    void findAllReturnsEveryAccount() {
        dao.create(sample(1000000001L));
        dao.create(sample(1000000002L));
        List<Account> all = dao.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void updateBalancePersistsExactValue() {
        dao.create(sample(1000000001L));
        dao.updateBalance(1000000001L, new BigDecimal("250.75"));
        assertEquals(0, new BigDecimal("250.75")
                .compareTo(dao.findByAccountNumber(1000000001L).orElseThrow().getBalance()));
    }

    @Test
    void updatePinPersists() {
        dao.create(sample(1000000001L));
        dao.updatePin(1000000001L, "4321");
        assertEquals("4321", dao.findByAccountNumber(1000000001L).orElseThrow().getPin());
    }

    @Test
    void updateStatusPersists() {
        dao.create(sample(1000000001L));
        dao.updateStatus(1000000001L, AccountStatus.BLOCKED);
        assertEquals(AccountStatus.BLOCKED,
                dao.findByAccountNumber(1000000001L).orElseThrow().getStatus());
    }
}
