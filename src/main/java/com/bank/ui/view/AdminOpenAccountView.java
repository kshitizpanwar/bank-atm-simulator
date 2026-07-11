package com.bank.ui.view;

public interface AdminOpenAccountView {
    String getHolderName();
    String getAccountType();
    String getPin();
    String getOpeningBalance();
    void showError(String message);
    void showMessage(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
