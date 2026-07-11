package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.AdminNavigator;
import com.bank.ui.Messages;
import com.bank.ui.view.AdminOpenAccountView;

import java.math.BigDecimal;

public class AdminOpenAccountPresenter {

    private final AdminOpenAccountView view;
    private final AccountService accountService;

    public AdminOpenAccountPresenter(AdminOpenAccountView view, AccountService accountService,
                                     AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showAdminMenu);
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
            view.showMessage("Account created. Number: " + account.getAccountNumber());
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
