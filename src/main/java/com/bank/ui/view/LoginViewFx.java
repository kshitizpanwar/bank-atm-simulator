package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginViewFx implements LoginView {

    private final TextField accountField = new TextField();
    private final PasswordField pinField = new PasswordField();
    private final Button loginButton = new Button("Login");
    private final Button openButton = new Button("Open New Account");
    private final Button backButton = new Button("Back");
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public LoginViewFx() {
        root.setPadding(new Insets(20));
        accountField.setPromptText("Account number");
        pinField.setPromptText("PIN");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("ATM Login"), accountField, pinField,
                loginButton, openButton, backButton, errorLabel);
    }

    public Parent getRoot() {
        return root;
    }

    @Override public String getAccountNumber() { return accountField.getText(); }
    @Override public String getPin() { return pinField.getText(); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnLogin(Runnable handler) { loginButton.setOnAction(e -> handler.run()); }
    @Override public void setOnOpenAccount(Runnable handler) { openButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
