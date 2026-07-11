package com.bank.ui.presenter;

import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AdminMenuView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminMenuPresenterTest {

    static class FakeAdminMenuView implements AdminMenuView {
        Runnable onAllAccounts, onOpenAccount, onLogout;
        @Override public void setOnAllAccounts(Runnable h) { onAllAccounts = h; }
        @Override public void setOnOpenAccount(Runnable h) { onOpenAccount = h; }
        @Override public void setOnLogout(Runnable h) { onLogout = h; }
    }

    @Test
    void buttonsRouteToNavigator() {
        FakeAdminMenuView view = new FakeAdminMenuView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        new AdminMenuPresenter(view, nav, new AdminSession());

        view.onAllAccounts.run();
        view.onOpenAccount.run();

        assertTrue(nav.allAccountsShown);
        assertTrue(nav.adminOpenAccountShown);
    }

    @Test
    void logoutClearsSessionAndReturnsToRoleSelect() {
        FakeAdminMenuView view = new FakeAdminMenuView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminSession session = new AdminSession();
        session.setAdmin("admin");
        new AdminMenuPresenter(view, nav, session);

        view.onLogout.run();

        assertFalse(session.isLoggedIn());
        assertTrue(nav.roleSelectShown);
    }
}
