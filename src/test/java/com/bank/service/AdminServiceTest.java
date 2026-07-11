package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

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
    }

    private long adminCount() {
        return uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) { throw new DaoException("count", e); }
        });
    }

    @Test
    void createAdminThenAuthenticate() {
        admins.createAdmin("manager", "secret");
        Admin a = auth.authenticateAdmin("manager", "secret");
        assertEquals("manager", a.getUsername());
        assertNotEquals("secret", a.getPasswordHash());
    }

    @Test
    void ensureDefaultAdminCreatesAdminOnceAndIsIdempotent() {
        admins.ensureDefaultAdmin();
        admins.ensureDefaultAdmin(); // second call must not add another
        assertEquals(1, adminCount());
        assertEquals("admin", auth.authenticateAdmin("admin", "admin123").getUsername());
    }
}
