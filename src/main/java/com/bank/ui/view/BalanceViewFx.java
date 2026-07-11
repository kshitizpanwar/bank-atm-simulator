package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class BalanceViewFx implements BalanceView {

    private final Label balanceLabel = new Label();
    private final Label errorLabel = new Label();
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public BalanceViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Balance"), balanceLabel, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showBalance(String balance) { balanceLabel.setText(balance); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
