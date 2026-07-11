package com.bank.service;

import com.bank.dao.AdminDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;

public class AdminService {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final UnitOfWork uow;
    private final AdminDao adminDao;
    private final PasswordHasher hasher;

    public AdminService(UnitOfWork uow, AdminDao adminDao, PasswordHasher hasher) {
        this.uow = uow;
        this.adminDao = adminDao;
        this.hasher = hasher;
    }

    public void createAdmin(String username, String rawPassword) {
        uow.executeVoid(c ->
                adminDao.create(c, new Admin(0L, username, hasher.hash(rawPassword))));
    }

    public void ensureDefaultAdmin() {
        uow.executeVoid(c -> {
            if (adminDao.findByUsername(c, DEFAULT_USERNAME).isEmpty()) {
                adminDao.create(c, new Admin(0L, DEFAULT_USERNAME, hasher.hash(DEFAULT_PASSWORD)));
            }
        });
    }
}
