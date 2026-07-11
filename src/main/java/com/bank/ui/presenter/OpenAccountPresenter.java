package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.view.OpenAccountView;

import java.math.BigDecimal;

public class OpenAccountPresenter {

    private final OpenAccountView view;
    private final AccountService accountService;
    private final Navigator navigator;

    public OpenAccountPresenter(OpenAccountView view, AccountService accountService, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.navigator = navigator;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showLogin);
    }

    public void submit() {
        BigDecimal opening;
        try {
            opening = new BigDecimal(view.getOpeningBalance().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        AccountType type;
        try {
            type = AccountType.valueOf(view.getAccountType());
        } catch (IllegalArgumentException e) {
            view.showError("Select an account type.");
            return;
        }
        try {
            Account account = accountService.openAccount(view.getHolderName(), view.getPin(), type, opening);
            view.setCreatedAccount(String.valueOf(account.getAccountNumber()));
            view.showMessage("Account created. Your number is " + account.getAccountNumber());
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
