package com.bank.db;

import com.bank.dao.DaoException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class UnitOfWorkTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final long ACC = 5000000001L;

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

    private void insertAccount(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(account_number,holder_name,pin,balance,account_type,status)"
                        + " VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, ACC);
            ps.setString(2, "Test");
            ps.setString(3, "hash");
            ps.setBigDecimal(4, new BigDecimal("10.00"));
            ps.setString(5, "SAVINGS");
            ps.setString(6, "ACTIVE");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("insert failed", e);
        }
    }

    private boolean accountExists() {
        return uow.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM accounts WHERE account_number=?")) {
                ps.setLong(1, ACC);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new DaoException("exists failed", e);
            }
        });
    }

    @Test
    void commitsOnSuccess() {
        uow.executeVoid(this::insertAccount);
        assertTrue(accountExists());
    }

    @Test
    void rollsBackOnException() {
        assertThrows(RuntimeException.class, () ->
            uow.executeVoid(c -> {
                insertAccount(c);
                throw new RuntimeException("boom"); // force rollback after a write
            }));
        assertFalse(accountExists(), "write must be rolled back");
    }
}
