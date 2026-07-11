package com.bank.ui.view;

import java.util.List;

public interface ManageAccountView {
    void showDetails(String details);
    void showHistory(List<String> lines);
    void showMessage(String message);
    void showError(String message);
    void setOnBlock(Runnable handler);
    void setOnClose(Runnable handler);
    void setOnUnblock(Runnable handler);
    void setOnReopen(Runnable handler);
    void setOnDelete(Runnable handler);
    void setOnBack(Runnable handler);
    void confirmDelete(String message, Runnable onConfirmed);
}
