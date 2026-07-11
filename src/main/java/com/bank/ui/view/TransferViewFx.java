package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class TransferViewFx implements TransferView {

    private final TextField targetField = new TextField();
    private final TextField amountField = new TextField();
    private final Button submitButton = new Button("Transfer");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public TransferViewFx() {
        root.setPadding(new Insets(20));
        targetField.setPromptText("Target account number");
        amountField.setPromptText("Amount");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Transfer"), targetField, amountField,
                submitButton, backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getTargetAccount() { return targetField.getText(); }
    @Override public String getAmount() { return amountField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
