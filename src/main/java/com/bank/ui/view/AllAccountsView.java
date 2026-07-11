package com.bank.ui.view;

import java.util.List;

public interface AllAccountsView {
    void showAccounts(List<AccountRow> rows);
    void showError(String message);
    AccountRow getSelected();
    void setOnManage(Runnable handler);
    void setOnBack(Runnable handler);
}
