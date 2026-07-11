package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AdminLoginViewFx implements AdminLoginView {

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Login");
    private final Button backButton = new Button("Back");
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public AdminLoginViewFx() {
        root.setPadding(new Insets(20));
        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        errorLabel.setStyle("-fx-text-fill: red;");
        Label hint = new Label("Default admin: admin / admin123 (change in production)");
        root.getChildren().addAll(new Label("Admin Login"), usernameField, passwordField,
                loginButton, backButton, errorLabel, hint);
    }

    public Parent getRoot() { return root; }

    @Override public String getUsername() { return usernameField.getText(); }
    @Override public String getPassword() { return passwordField.getText(); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnLogin(Runnable handler) { loginButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
