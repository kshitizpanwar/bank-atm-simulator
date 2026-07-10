package com.bank.dao;

import com.bank.db.Database;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDaoJdbc implements TransactionDao {

    private final Database db;

    public TransactionDaoJdbc(Database db) {
        this.db = db;
    }

    @Override
    public void insert(Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(account_number, type, amount, balance_after) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
    public List<Transaction> findByAccountNumber(long accountNumber) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY id DESC";
        return query(sql, accountNumber, null);
    }

    @Override
    public List<Transaction> findRecent(long accountNumber, int limit) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? "
                + "ORDER BY id DESC LIMIT ?";
        return query(sql, accountNumber, limit);
    }

    private List<Transaction> query(String sql, long accountNumber, Integer limit) {
        List<Transaction> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
