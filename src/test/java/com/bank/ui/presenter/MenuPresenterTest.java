package com.bank.ui.presenter;

import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.MenuView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuPresenterTest {

    static class FakeMenuView implements MenuView {
        Runnable onBalance, onDeposit, onWithdraw, onTransfer, onMiniStatement, onChangePin, onLogout;
        @Override public void setOnBalance(Runnable h) { onBalance = h; }
        @Override public void setOnDeposit(Runnable h) { onDeposit = h; }
        @Override public void setOnWithdraw(Runnable h) { onWithdraw = h; }
        @Override public void setOnTransfer(Runnable h) { onTransfer = h; }
        @Override public void setOnMiniStatement(Runnable h) { onMiniStatement = h; }
        @Override public void setOnChangePin(Runnable h) { onChangePin = h; }
        @Override public void setOnLogout(Runnable h) { onLogout = h; }
    }

    @Test
    void buttonsRouteToNavigator() {
        FakeMenuView view = new FakeMenuView();
        FakeNavigator nav = new FakeNavigator();
        new MenuPresenter(view, nav, new Session());

        view.onBalance.run();
        view.onDeposit.run();
        view.onTransfer.run();

        assertTrue(nav.balanceShown);
        assertTrue(nav.depositShown);
        assertTrue(nav.transferShown);

        view.onWithdraw.run();
        view.onMiniStatement.run();
        view.onChangePin.run();

        assertTrue(nav.withdrawShown);
        assertTrue(nav.miniStatementShown);
        assertTrue(nav.changePinShown);
    }

    @Test
    void logoutClearsSessionAndReturnsToLogin() {
        FakeMenuView view = new FakeMenuView();
        FakeNavigator nav = new FakeNavigator();
        Session session = new Session();
        session.setAccount(1000000001L);
        new MenuPresenter(view, nav, session);

        view.onLogout.run();

        assertFalse(session.isLoggedIn());
        assertTrue(nav.loginShown);
    }
}
