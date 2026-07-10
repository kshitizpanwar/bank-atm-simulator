package com.bank.dao;

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

    @Override
    public void create(Connection c, Account account) {
        String sql = "INSERT INTO accounts "
                + "(account_number, holder_name, pin, balance, account_type, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
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
    public Optional<Account> findByAccountNumber(Connection c, long accountNumber) {
        String sql = "SELECT account_number, holder_name, pin, balance, account_type, status, created_at "
                + "FROM accounts WHERE account_number = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByAccountNumber failed", e);
        }
    }

    @Override
    public List<Account> findAll(Connection c) {
        String sql = "SELECT account_number, holder_name, pin, balance, account_type, status, created_at "
                + "FROM accounts ORDER BY account_number";
        List<Account> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
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
    public void updateBalance(Connection c, long accountNumber, BigDecimal newBalance) {
        runUpdate(c, "UPDATE accounts SET balance = ? WHERE account_number = ?",
                ps -> {
                    ps.setBigDecimal(1, newBalance);
                    ps.setLong(2, accountNumber);
                }, "updateBalance");
    }

    @Override
    public void updatePin(Connection c, long accountNumber, String newPinHash) {
        runUpdate(c, "UPDATE accounts SET pin = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, newPinHash);
                    ps.setLong(2, accountNumber);
                }, "updatePin");
    }

    @Override
    public void updateStatus(Connection c, long accountNumber, AccountStatus status) {
        runUpdate(c, "UPDATE accounts SET status = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, status.name());
                    ps.setLong(2, accountNumber);
                }, "updateStatus");
    }

    private void runUpdate(Connection c, String sql, StatementBinder binder, String label) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
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
