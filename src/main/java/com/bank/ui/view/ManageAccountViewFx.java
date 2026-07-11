package com.bank.ui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class ManageAccountViewFx implements ManageAccountView {

    private final Label detailsLabel = new Label();
    private final ListView<String> history = new ListView<>();
    private final Button blockButton = new Button("Block");
    private final Button closeButton = new Button("Close");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public ManageAccountViewFx() {
        root.setPadding(new Insets(20));
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        HBox actions = new HBox(10, blockButton, closeButton, backButton);
        root.getChildren().addAll(new Label("Manage Account"), detailsLabel,
                new Label("Transactions:"), history, actions, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showDetails(String details) { detailsLabel.setText(details); }
    @Override public void showHistory(List<String> lines) {
        history.setItems(FXCollections.observableArrayList(lines));
    }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnBlock(Runnable handler) { blockButton.setOnAction(e -> handler.run()); }
    @Override public void setOnClose(Runnable handler) { closeButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
