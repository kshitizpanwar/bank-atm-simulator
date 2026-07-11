package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.TransferView;

import java.math.BigDecimal;

public class TransferPresenter {

    private final TransferView view;
    private final AccountService accountService;
    private final Session session;

    public TransferPresenter(TransferView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        long target;
        BigDecimal amount;
        try {
            target = Long.parseLong(view.getTargetAccount().trim());
            amount = new BigDecimal(view.getAmount().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            accountService.transfer(session.requireAccount(), target, amount);
            view.showMessage("Transfer complete.");
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
