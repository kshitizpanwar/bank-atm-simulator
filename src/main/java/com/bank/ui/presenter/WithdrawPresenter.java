package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.WithdrawView;

import java.math.BigDecimal;

public class WithdrawPresenter {

    private final WithdrawView view;
    private final AccountService accountService;
    private final Session session;

    public WithdrawPresenter(WithdrawView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        BigDecimal amount;
        try {
            amount = new BigDecimal(view.getAmount().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            long account = session.requireAccount();
            accountService.withdraw(account, amount);
            view.showMessage("Withdrew. New balance: " + accountService.getBalance(account));
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
