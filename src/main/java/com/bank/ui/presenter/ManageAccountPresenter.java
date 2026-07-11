package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.Messages;
import com.bank.ui.view.ManageAccountView;

import java.util.ArrayList;
import java.util.List;

public class ManageAccountPresenter {

    private final ManageAccountView view;
    private final AccountService accountService;
    private final AdminSession session;
    private final AdminNavigator navigator;

    public ManageAccountPresenter(ManageAccountView view, AccountService accountService,
                                  AdminSession session, AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        this.navigator = navigator;
        view.setOnBlock(this::block);
        view.setOnClose(this::close);
        view.setOnDelete(this::delete);
        view.setOnBack(navigator::showAllAccounts);
    }

    public void load() {
        try {
            long acct = session.requireSelectedAccount();
            Account a = accountService.getAccount(acct);
            view.showDetails("Account " + a.getAccountNumber() + "  " + a.getHolderName()
                    + "  " + a.getAccountType() + "  balance " + a.getBalance()
                    + "  status " + a.getStatus());
            List<String> lines = new ArrayList<>();
            for (Transaction t : accountService.accountHistory(acct)) {
                lines.add(t.getTimestamp() + "  " + t.getType() + "  " + t.getAmount()
                        + "  (balance " + t.getBalanceAfter() + ")");
            }
            view.showHistory(lines);
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }

    public void delete() {
        long acct = session.requireSelectedAccount();
        view.confirmDelete("Delete account " + acct + "? This cannot be undone.",
                () -> performDelete(acct));
    }

    private void performDelete(long acct) {
        try {
            accountService.deleteAccount(acct);
            navigator.showAllAccounts();
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }

    public void block() {
        applyStatus(true);
    }

    public void close() {
        applyStatus(false);
    }

    private void applyStatus(boolean block) {
        try {
            long acct = session.requireSelectedAccount();
            if (block) {
                accountService.blockAccount(acct);
                view.showMessage("Account blocked.");
            } else {
                accountService.closeAccount(acct);
                view.showMessage("Account closed.");
            }
            load();
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
