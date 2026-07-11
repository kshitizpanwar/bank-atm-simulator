package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.ChangePinView;

public class ChangePinPresenter {

    private final ChangePinView view;
    private final AccountService accountService;
    private final Session session;

    public ChangePinPresenter(ChangePinView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        try {
            accountService.changePin(session.requireAccount(), view.getOldPin(), view.getNewPin());
            view.showMessage("PIN changed.");
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
