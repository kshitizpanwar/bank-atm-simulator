package com.bank.ui.view;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public class AllAccountsViewFx implements AllAccountsView {

    private final TextField searchField = new TextField();
    private final Button refreshButton = new Button("Refresh");
    private final Button manageButton = new Button("Manage Selected");
    private final TableView<AccountRow> table = new TableView<>();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(12);
    private Consumer<String> onSearch = q -> { };

    public AllAccountsViewFx() {
        TableColumn<AccountRow, String> noCol = new TableColumn<>("No");
        noCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                String.valueOf(table.getItems().indexOf(cd.getValue()) + 1)));
        TableColumn<AccountRow, String> acctCol = new TableColumn<>("Account #");
        acctCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().accountNumber())));
        TableColumn<AccountRow, String> holderCol = new TableColumn<>("Holder");
        holderCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().holderName()));
        TableColumn<AccountRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().accountType()));
        TableColumn<AccountRow, String> balCol = new TableColumn<>("Balance");
        balCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().balance()));
        TableColumn<AccountRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().status()));
        table.getColumns().add(noCol);
        table.getColumns().add(acctCol);
        table.getColumns().add(holderCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(balCol);
        table.getColumns().add(statusCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        searchField.setPromptText("Search by name");
        searchField.textProperty().addListener((obs, oldV, newV) -> onSearch.accept(newV));
        refreshButton.setOnAction(e -> onSearch.accept(searchField.getText()));
        HBox searchBar = new HBox(10, new Label("Search by Name"), searchField, refreshButton);

        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("All Accounts"), searchBar, table, manageButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showAccounts(List<AccountRow> rows) {
        table.setItems(FXCollections.observableArrayList(rows));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public AccountRow getSelected() { return table.getSelectionModel().getSelectedItem(); }
    @Override public void setOnSearch(Consumer<String> handler) { this.onSearch = handler; }
    @Override public void setOnManage(Runnable handler) { manageButton.setOnAction(e -> handler.run()); }
}
