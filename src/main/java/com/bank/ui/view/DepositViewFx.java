package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class DepositViewFx implements DepositView {

    private final TextField amountField = new TextField();
    private final Button submitButton = new Button("Deposit");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public DepositViewFx() {
        root.setPadding(new Insets(20));
        amountField.setPromptText("Amount");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Deposit"), amountField, submitButton,
                backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getAmount() { return amountField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
