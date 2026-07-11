package com.bank.ui.view;

public interface WithdrawView {
    String getAmount();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
