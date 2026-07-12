package com.bank.ui.view;

import java.util.List;
import java.util.function.Consumer;

public interface AllAccountsView {
    void showAccounts(List<AccountRow> rows);
    void showError(String message);
    AccountRow getSelected();
    void setOnSearch(Consumer<String> handler);
    void setOnManage(Runnable handler);
}
