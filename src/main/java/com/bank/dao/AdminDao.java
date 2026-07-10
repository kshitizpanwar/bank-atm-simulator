package com.bank.dao;

import com.bank.model.Admin;

import java.sql.Connection;
import java.util.Optional;

public interface AdminDao {
    Optional<Admin> findByUsername(Connection c, String username);
    void create(Connection c, Admin admin);
}
