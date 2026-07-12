package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.service.AccountService;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AccountRow;
import com.bank.ui.view.AllAccountsView;

import java.util.ArrayList;
import java.util.List;

public class AllAccountsPresenter {

    private final AllAccountsView view;
    private final AccountService accountService;
    private final AdminSession session;
    private final AdminNavigator navigator;

    public AllAccountsPresenter(AllAccountsView view, AccountService accountService,
                                AdminSession session, AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        this.navigator = navigator;
        view.setOnSearch(this::search);
        view.setOnManage(this::manage);
    }

    public void load() {
        search("");
    }

    public void search(String query) {
        try {
            String q = query == null ? "" : query.trim().toLowerCase();
            List<AccountRow> rows = new ArrayList<>();
            for (Account a : accountService.listAllAccounts()) {
                if (q.isEmpty() || a.getHolderName().toLowerCase().contains(q)) {
                    rows.add(new AccountRow(a.getAccountNumber(), a.getHolderName(),
                            a.getAccountType().name(), a.getBalance().toString(), a.getStatus().name()));
                }
            }
            view.showAccounts(rows);
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }

    public void manage() {
        AccountRow selected = view.getSelected();
        if (selected == null) {
            view.showError("Select an account first.");
            return;
        }
        session.selectAccount(selected.accountNumber());
        navigator.showManageAccount(selected.accountNumber());
    }
}
