package com.bank.db;

import com.bank.dao.DaoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(Properties props) {
        this.url = props.getProperty("db.url");
        this.user = props.getProperty("db.user");
        this.password = props.getProperty("db.password", "");
    }

    public static Database fromResource(String resourceName) {
        Properties props = new Properties();
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Config resource not found: " + resourceName);
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourceName, e);
        }
        return new Database(props);
    }

    public static Database defaultInstance() {
        return fromResource("db.properties");
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DaoException("Failed to open connection to " + url, e);
        }
    }
}
