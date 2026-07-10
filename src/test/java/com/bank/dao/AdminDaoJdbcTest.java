package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdminDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AdminDao dao = new AdminDaoJdbc();

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    @Test
    void createThenFindByUsername() {
        uow.executeVoid(c -> dao.create(c, new Admin(0L, "manager", "hashed-pw")));
        Optional<Admin> found = uow.execute(c -> dao.findByUsername(c, "manager"));
        assertTrue(found.isPresent());
        assertEquals("manager", found.get().getUsername());
        assertEquals("hashed-pw", found.get().getPasswordHash());
        assertTrue(found.get().getId() > 0);
    }

    @Test
    void findByUsernameReturnsEmptyWhenAbsent() {
        assertTrue(uow.execute(c -> dao.findByUsername(c, "nobody")).isEmpty());
    }
}
