package com.bank.ui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.List;

public class MiniStatementViewFx implements MiniStatementView {

    private final ListView<String> list = new ListView<>();
    private final Label errorLabel = new Label();
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public MiniStatementViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Mini Statement"), list, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showTransactions(List<String> lines) {
        list.setItems(FXCollections.observableArrayList(lines));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
