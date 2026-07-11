package com.bank.ui.view;

public interface MenuView {
    void setOnBalance(Runnable handler);
    void setOnDeposit(Runnable handler);
    void setOnWithdraw(Runnable handler);
    void setOnTransfer(Runnable handler);
    void setOnMiniStatement(Runnable handler);
    void setOnChangePin(Runnable handler);
    void setOnLogout(Runnable handler);
}
