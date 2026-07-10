package com.bank.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaInitializerTest {

    private final Database db = Database.fromResource("db.test.properties");

    @Test
    void initializeCreatesAllThreeTables() throws Exception {
        SchemaInitializer.initialize(db);   // idempotent — safe to run repeatedly

        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            for (String table : new String[]{"accounts", "transactions", "admins"}) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rs.next(), "table missing: " + table);
                }
            }
        }
    }

    @Test
    void getConnectionSucceeds() throws Exception {
        try (Connection c = db.getConnection()) {
            assertTrue(c.isValid(2));
        }
    }
}
