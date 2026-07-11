package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.service.AuthService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.LoginView;

public class LoginPresenter {

    private final LoginView view;
    private final AuthService authService;
    private final Navigator navigator;
    private final Session session;

    public LoginPresenter(LoginView view, AuthService authService, Navigator navigator, Session session) {
        this.view = view;
        this.authService = authService;
        this.navigator = navigator;
        this.session = session;
        view.setOnLogin(this::login);
        view.setOnOpenAccount(navigator::showOpenAccount);
    }

    public void login() {
        long accountNumber;
        try {
            accountNumber = Long.parseLong(view.getAccountNumber().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            Account account = authService.authenticateCustomer(accountNumber, view.getPin());
            session.setAccount(account.getAccountNumber());
            navigator.showMenu();
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
