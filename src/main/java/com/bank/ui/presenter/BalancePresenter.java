package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.BalanceView;

public class BalancePresenter {

    private final BalanceView view;
    private final AccountService accountService;
    private final Session session;

    public BalancePresenter(BalanceView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnBack(navigator::showMenu);
    }

    public void load() {
        try {
            view.showBalance("Balance: " + accountService.getBalance(session.requireAccount()));
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
