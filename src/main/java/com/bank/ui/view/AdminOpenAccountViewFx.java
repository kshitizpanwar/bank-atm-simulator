package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AdminOpenAccountViewFx implements AdminOpenAccountView {

    private final TextField nameField = new TextField();
    private final ChoiceBox<String> typeChoice = new ChoiceBox<>();
    private final PasswordField pinField = new PasswordField();
    private final TextField openingField = new TextField();
    private final Button submitButton = new Button("Create Account");
    private final Button backButton = new Button("Back");
    private final Label errorLabel = new Label();
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(10);

    public AdminOpenAccountViewFx() {
        root.setPadding(new Insets(20));
        typeChoice.getItems().addAll("SAVINGS", "CURRENT");
        typeChoice.setValue("SAVINGS");
        nameField.setPromptText("Full name");
        pinField.setPromptText("4-digit PIN");
        openingField.setPromptText("Opening balance");
        errorLabel.setStyle("-fx-text-fill: red;");
        messageLabel.setStyle("-fx-text-fill: green;");
        root.getChildren().addAll(new Label("Open Account (Admin)"), nameField, typeChoice,
                pinField, openingField, submitButton, backButton, errorLabel, messageLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getHolderName() { return nameField.getText(); }
    @Override public String getAccountType() { return typeChoice.getValue(); }
    @Override public String getPin() { return pinField.getText(); }
    @Override public String getOpeningBalance() { return openingField.getText(); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
