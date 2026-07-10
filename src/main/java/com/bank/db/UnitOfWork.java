package com.bank.db;

import com.bank.dao.DaoException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class UnitOfWork {

    private final Database database;

    public UnitOfWork(Database database) {
        this.database = database;
    }

    public <T> T execute(Function<Connection, T> work) {
        Connection c = null;
        try {
            c = database.getConnection();
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException | Error e) {
                safeRollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DaoException("transaction failed", e);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // closing best-effort; original outcome already decided
                }
            }
        }
    }

    private void safeRollback(Connection c, Throwable original) {
        try {
            c.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    public void executeVoid(Consumer<Connection> work) {
        execute(c -> {
            work.accept(c);
            return null;
        });
    }
}
