package com.bank.ui.view;

public interface AdminLoginView {
    String getUsername();
    String getPassword();
    void showError(String message);
    void setOnLogin(Runnable handler);
    void setOnBack(Runnable handler);
}
