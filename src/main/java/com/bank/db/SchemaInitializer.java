package com.bank.db;

import com.bank.dao.DaoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaInitializer {

    private SchemaInitializer() { }

    public static void initialize(Database db) {
        String script = readScript("schema.sql");
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            for (String statement : script.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Schema initialization failed", e);
        }
    }

    private static String readScript(String resourceName) {
        try (InputStream in = SchemaInitializer.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Script not found: " + resourceName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourceName, e);
        }
    }
}
