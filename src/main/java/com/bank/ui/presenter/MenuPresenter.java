package com.bank.ui.presenter;

import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.MenuView;

public class MenuPresenter {

    private final Navigator navigator;
    private final Session session;

    public MenuPresenter(MenuView view, Navigator navigator, Session session) {
        this.navigator = navigator;
        this.session = session;
        view.setOnBalance(navigator::showBalance);
        view.setOnDeposit(navigator::showDeposit);
        view.setOnWithdraw(navigator::showWithdraw);
        view.setOnTransfer(navigator::showTransfer);
        view.setOnMiniStatement(navigator::showMiniStatement);
        view.setOnChangePin(navigator::showChangePin);
        view.setOnLogout(this::logout);
    }

    public void logout() {
        session.clear();
        navigator.showLogin();
    }
}
