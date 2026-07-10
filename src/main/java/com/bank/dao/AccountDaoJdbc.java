package com.bank.dao;

import com.bank.db.Database;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDaoJdbc implements AccountDao {

    private final Database db;

    public AccountDaoJdbc(Database db) {
        this.db = db;
    }

    @Override
    public void create(Account account) {
        String sql = "INSERT INTO accounts "
                + "(account_number, holder_name, pin, balance, account_type, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, account.getAccountNumber());
            ps.setString(2, account.getHolderName());
            ps.setString(3, account.getPin());
            ps.setBigDecimal(4, account.getBalance());
            ps.setString(5, account.getAccountType().name());
            ps.setString(6, account.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("create account failed", e);
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(long accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByAccountNumber failed", e);
        }
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT * FROM accounts ORDER BY account_number";
        List<Account> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DaoException("findAll failed", e);
        }
    }

    @Override
    public void updateBalance(long accountNumber, BigDecimal newBalance) {
        runUpdate("UPDATE accounts SET balance = ? WHERE account_number = ?",
                ps -> {
                    ps.setBigDecimal(1, newBalance);
                    ps.setLong(2, accountNumber);
                }, "updateBalance");
    }

    @Override
    public void updatePin(long accountNumber, String newPin) {
        runUpdate("UPDATE accounts SET pin = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, newPin);
                    ps.setLong(2, accountNumber);
                }, "updatePin");
    }

    @Override
    public void updateStatus(long accountNumber, AccountStatus status) {
        runUpdate("UPDATE accounts SET status = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, status.name());
                    ps.setLong(2, accountNumber);
                }, "updateStatus");
    }

    private void runUpdate(String sql, StatementBinder binder, String label) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(label + " failed", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return new Account(
                rs.getLong("account_number"),
                rs.getString("holder_name"),
                rs.getString("pin"),
                rs.getBigDecimal("balance"),
                AccountType.valueOf(rs.getString("account_type")),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
