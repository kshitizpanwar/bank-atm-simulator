package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private final AccountDao accountDao = new AccountDaoJdbc(db);
    private final TransactionDao txDao = new TransactionDaoJdbc(db);

    private static final long ACC = 1000000001L;

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanAndSeedAccount() throws Exception {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM accounts");
        }
        accountDao.create(new Account(ACC, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now()));
    }

    private Transaction tx(TransactionType type, String amount, String balanceAfter) {
        return new Transaction(0L, ACC, type, new BigDecimal(amount),
                new BigDecimal(balanceAfter), LocalDateTime.now());
    }

    @Test
    void insertAssignsGeneratedId() {
        txDao.insert(tx(TransactionType.DEPOSIT, "50.00", "150.00"));
        List<Transaction> found = txDao.findByAccountNumber(ACC);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getId() > 0);
        assertEquals(TransactionType.DEPOSIT, found.get(0).getType());
        assertEquals(0, new BigDecimal("50.00").compareTo(found.get(0).getAmount()));
    }

    @Test
    void findByAccountNumberReturnsAllForAccount() {
        txDao.insert(tx(TransactionType.DEPOSIT, "50.00", "150.00"));
        txDao.insert(tx(TransactionType.WITHDRAW, "20.00", "130.00"));
        assertEquals(2, txDao.findByAccountNumber(ACC).size());
    }

    @Test
    void findRecentRespectsLimitAndNewestFirst() {
        txDao.insert(tx(TransactionType.DEPOSIT, "10.00", "110.00"));
        txDao.insert(tx(TransactionType.DEPOSIT, "20.00", "130.00"));
        txDao.insert(tx(TransactionType.DEPOSIT, "30.00", "160.00"));

        List<Transaction> recent = txDao.findRecent(ACC, 2);
        assertEquals(2, recent.size());
        // newest first: the last inserted (id largest) comes first
        assertTrue(recent.get(0).getId() > recent.get(1).getId());
    }
}
