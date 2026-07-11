package com.bank.ui.presenter;

import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AdminMenuView;

public class AdminMenuPresenter {

    private final AdminNavigator navigator;
    private final AdminSession session;

    public AdminMenuPresenter(AdminMenuView view, AdminNavigator navigator, AdminSession session) {
        this.navigator = navigator;
        this.session = session;
        view.setOnAllAccounts(navigator::showAllAccounts);
        view.setOnOpenAccount(navigator::showAdminOpenAccount);
        view.setOnLogout(this::logout);
    }

    public void logout() {
        session.clear();
        navigator.showRoleSelect();
    }
}
