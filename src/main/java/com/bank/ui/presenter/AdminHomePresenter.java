package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.service.AccountService;
import com.bank.ui.view.AdminHomeView;

import java.util.List;

public class AdminHomePresenter {

    private final AdminHomeView view;
    private final AccountService accountService;

    public AdminHomePresenter(AdminHomeView view, AccountService accountService) {
        this.view = view;
        this.accountService = accountService;
    }

    public void load() {
        try {
            List<Account> all = accountService.listAllAccounts();
            long active = all.stream().filter(a -> a.getStatus() == AccountStatus.ACTIVE).count();
            long blocked = all.stream().filter(a -> a.getStatus() == AccountStatus.BLOCKED).count();
            long closed = all.stream().filter(a -> a.getStatus() == AccountStatus.CLOSED).count();
            view.showSummary("Total accounts: " + all.size()
                    + "\nActive: " + active
                    + "\nBlocked: " + blocked
                    + "\nClosed: " + closed);
        } catch (RuntimeException e) {
            view.showSummary("Unable to load summary.");
        }
    }
}
