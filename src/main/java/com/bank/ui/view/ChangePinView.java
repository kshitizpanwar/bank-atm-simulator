package com.bank.ui.view;

public interface ChangePinView {
    String getOldPin();
    String getNewPin();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
