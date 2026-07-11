package com.bank.ui.view;

import java.util.List;

public interface MiniStatementView {
    void showTransactions(List<String> lines);
    void showError(String message);
    void setOnBack(Runnable handler);
}
