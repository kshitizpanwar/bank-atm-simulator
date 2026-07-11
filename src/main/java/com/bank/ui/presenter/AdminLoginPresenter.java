package com.bank.ui.presenter;

import com.bank.model.Admin;
import com.bank.service.AuthenticationException;
import com.bank.service.AuthService;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AdminLoginView;

public class AdminLoginPresenter {

    private final AdminLoginView view;
    private final AuthService authService;
    private final AdminNavigator navigator;
    private final AdminSession session;

    public AdminLoginPresenter(AdminLoginView view, AuthService authService,
                               AdminNavigator navigator, AdminSession session) {
        this.view = view;
        this.authService = authService;
        this.navigator = navigator;
        this.session = session;
        view.setOnLogin(this::login);
    }

    public void login() {
        try {
            Admin admin = authService.authenticateAdmin(view.getUsername(), view.getPassword());
            session.setAdmin(admin.getUsername());
            navigator.showAdminMenu();
        } catch (AuthenticationException e) {
            view.showError("Invalid username or password.");
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
