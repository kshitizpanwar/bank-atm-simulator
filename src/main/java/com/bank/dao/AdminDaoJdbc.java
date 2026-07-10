package com.bank.dao;

import com.bank.model.Admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AdminDaoJdbc implements AdminDao {

    @Override
    public Optional<Admin> findByUsername(Connection c, String username) {
        String sql = "SELECT id, username, password FROM admins WHERE username = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Admin(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password")));
            }
        } catch (SQLException e) {
            throw new DaoException("findByUsername failed", e);
        }
    }

    @Override
    public void create(Connection c, Admin admin) {
        String sql = "INSERT INTO admins (username, password) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, admin.getUsername());
            ps.setString(2, admin.getPasswordHash());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("create admin failed", e);
        }
    }
}
