package com.bank.ui;

public class FakeAdminNavigator implements AdminNavigator {
    public boolean roleSelectShown, adminLoginShown, adminMenuShown,
            allAccountsShown, adminOpenAccountShown;
    public Long manageAccountShownFor;

    @Override public void showRoleSelect() { roleSelectShown = true; }
    @Override public void showAdminLogin() { adminLoginShown = true; }
    @Override public void showAdminMenu() { adminMenuShown = true; }
    @Override public void showAllAccounts() { allAccountsShown = true; }
    @Override public void showAdminOpenAccount() { adminOpenAccountShown = true; }
    @Override public void showManageAccount(long accountNumber) { manageAccountShownFor = accountNumber; }
}
