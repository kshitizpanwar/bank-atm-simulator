package com.bank.dao;

import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDaoJdbc implements TransactionDao {

    @Override
    public void insert(Connection c, Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(account_number, type, amount, balance_after) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, transaction.getAccountNumber());
            ps.setString(2, transaction.getType().name());
            ps.setBigDecimal(3, transaction.getAmount());
            ps.setBigDecimal(4, transaction.getBalanceAfter());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("insert transaction failed", e);
        }
    }

    @Override
    public List<Transaction> findByAccountNumber(Connection c, long accountNumber) {
        String sql = "SELECT id, account_number, type, amount, balance_after, `timestamp` "
                + "FROM transactions WHERE account_number = ? ORDER BY id DESC";
        return query(c, sql, accountNumber, null);
    }

    @Override
    public List<Transaction> findRecent(Connection c, long accountNumber, int limit) {
        String sql = "SELECT id, account_number, type, amount, balance_after, `timestamp` "
                + "FROM transactions WHERE account_number = ? ORDER BY id DESC LIMIT ?";
        return query(c, sql, accountNumber, limit);
    }

    @Override
    public void deleteByAccountNumber(Connection c, long accountNumber) {
        try (PreparedStatement ps =
                     c.prepareStatement("DELETE FROM transactions WHERE account_number = ?")) {
            ps.setLong(1, accountNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("delete transactions failed", e);
        }
    }

    private List<Transaction> query(Connection c, String sql, long accountNumber, Integer limit) {
        List<Transaction> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            if (limit != null) {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DaoException("query transactions failed", e);
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getLong("account_number"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                rs.getTimestamp("timestamp").toLocalDateTime());
    }
}
