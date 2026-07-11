package com.bank.ui.view;

public interface LoginView {
    String getAccountNumber();
    String getPin();
    void showError(String message);
    void setOnLogin(Runnable handler);
    void setOnOpenAccount(Runnable handler);
    void setOnBack(Runnable handler);
}
