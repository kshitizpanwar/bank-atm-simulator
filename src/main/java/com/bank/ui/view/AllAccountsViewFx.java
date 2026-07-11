package com.bank.ui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.List;

public class AllAccountsViewFx implements AllAccountsView {

    private final ListView<AccountRow> list = new ListView<>();
    private final Label errorLabel = new Label();
    private final Button manageButton = new Button("Manage Selected");
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public AllAccountsViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(AccountRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.text());
            }
        });
        root.getChildren().addAll(new Label("All Accounts"), list, manageButton, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showAccounts(List<AccountRow> rows) {
        list.setItems(FXCollections.observableArrayList(rows));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public AccountRow getSelected() { return list.getSelectionModel().getSelectedItem(); }
    @Override public void setOnManage(Runnable handler) { manageButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
