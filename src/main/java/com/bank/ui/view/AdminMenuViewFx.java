package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AdminMenuViewFx implements AdminMenuView {

    private final Button allAccounts = new Button("All Accounts");
    private final Button openAccount = new Button("Open Account");
    private final Button logout = new Button("Logout");
    private final VBox root = new VBox(10);

    public AdminMenuViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Admin Menu"), allAccounts, openAccount, logout);
    }

    public Parent getRoot() { return root; }

    @Override public void setOnAllAccounts(Runnable h) { allAccounts.setOnAction(e -> h.run()); }
    @Override public void setOnOpenAccount(Runnable h) { openAccount.setOnAction(e -> h.run()); }
    @Override public void setOnLogout(Runnable h) { logout.setOnAction(e -> h.run()); }
}
