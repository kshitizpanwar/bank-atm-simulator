package com.bank.ui.view;

public interface BalanceView {
    void showBalance(String balance);
    void showError(String message);
    void setOnBack(Runnable handler);
}
