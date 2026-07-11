package com.bank.ui.view;

public interface AdminMenuView {
    void setOnAllAccounts(Runnable handler);
    void setOnOpenAccount(Runnable handler);
    void setOnLogout(Runnable handler);
}
