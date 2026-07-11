package com.bank.ui.presenter;

import com.bank.model.Transaction;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.MiniStatementView;

import java.util.ArrayList;
import java.util.List;

public class MiniStatementPresenter {

    private static final int RECENT_COUNT = 10;

    private final MiniStatementView view;
    private final AccountService accountService;
    private final Session session;

    public MiniStatementPresenter(MiniStatementView view, AccountService accountService,
                                  Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnBack(navigator::showMenu);
    }

    public void load() {
        try {
            List<Transaction> transactions =
                    accountService.miniStatement(session.requireAccount(), RECENT_COUNT);
            List<String> lines = new ArrayList<>();
            for (Transaction t : transactions) {
                lines.add(t.getTimestamp() + "  " + t.getType() + "  " + t.getAmount()
                        + "  (balance " + t.getBalanceAfter() + ")");
            }
            view.showTransactions(lines);
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
