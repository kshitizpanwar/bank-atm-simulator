package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MenuViewFx implements MenuView {

    private final Button balance = new Button("Balance");
    private final Button deposit = new Button("Deposit");
    private final Button withdraw = new Button("Withdraw");
    private final Button transfer = new Button("Transfer");
    private final Button miniStatement = new Button("Mini Statement");
    private final Button changePin = new Button("Change PIN");
    private final Button logout = new Button("Logout");
    private final VBox root = new VBox(10);

    public MenuViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Main Menu"), balance, deposit, withdraw,
                transfer, miniStatement, changePin, logout);
    }

    public Parent getRoot() { return root; }

    @Override public void setOnBalance(Runnable h) { balance.setOnAction(e -> h.run()); }
    @Override public void setOnDeposit(Runnable h) { deposit.setOnAction(e -> h.run()); }
    @Override public void setOnWithdraw(Runnable h) { withdraw.setOnAction(e -> h.run()); }
    @Override public void setOnTransfer(Runnable h) { transfer.setOnAction(e -> h.run()); }
    @Override public void setOnMiniStatement(Runnable h) { miniStatement.setOnAction(e -> h.run()); }
    @Override public void setOnChangePin(Runnable h) { changePin.setOnAction(e -> h.run()); }
    @Override public void setOnLogout(Runnable h) { logout.setOnAction(e -> h.run()); }
}
