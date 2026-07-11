package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

public class ChangePinViewFx implements ChangePinView {

    private final PasswordField oldPinField = new PasswordField();
    private final PasswordField newPinField = new PasswordField();
    private final Button submitButton = new Button("Change PIN");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public ChangePinViewFx() {
        root.setPadding(new Insets(20));
        oldPinField.setPromptText("Current PIN");
        newPinField.setPromptText("New 4-digit PIN");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Change PIN"), oldPinField, newPinField,
                submitButton, backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getOldPin() { return oldPinField.getText(); }
    @Override public String getNewPin() { return newPinField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
