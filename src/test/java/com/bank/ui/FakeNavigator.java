package com.bank.ui;

public class FakeNavigator implements Navigator {
    public boolean loginShown, openAccountShown, menuShown, balanceShown,
            depositShown, withdrawShown, transferShown, miniStatementShown, changePinShown;

    @Override public void showLogin() { loginShown = true; }
    @Override public void showOpenAccount() { openAccountShown = true; }
    @Override public void showMenu() { menuShown = true; }
    @Override public void showBalance() { balanceShown = true; }
    @Override public void showDeposit() { depositShown = true; }
    @Override public void showWithdraw() { withdrawShown = true; }
    @Override public void showTransfer() { transferShown = true; }
    @Override public void showMiniStatement() { miniStatementShown = true; }
    @Override public void showChangePin() { changePinShown = true; }
}
