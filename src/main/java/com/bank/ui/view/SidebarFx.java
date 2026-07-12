package com.bank.ui.view;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SidebarFx {

    private final Button homeButton = new Button("Home");
    private final Button allAccountsButton = new Button("All Accounts");
    private final Button openAccountButton = new Button("Open Account");
    private final Button logoutButton = new Button("Log Out");
    private final VBox root = new VBox(6);

    public SidebarFx() {
        root.getStyleClass().add("sidebar");
        root.setPrefWidth(200);
        Label brand = new Label("Sky Bank");
        brand.getStyleClass().add("brand");
        for (Button b : new Button[]{homeButton, allAccountsButton, openAccountButton, logoutButton}) {
            b.getStyleClass().add("sidebar-item");
            b.setMaxWidth(Double.MAX_VALUE);
        }
        root.getChildren().addAll(brand, homeButton, allAccountsButton, openAccountButton, logoutButton);
    }

    public Parent getRoot() { return root; }

    public void setOnHome(Runnable h) { homeButton.setOnAction(e -> h.run()); }
    public void setOnAllAccounts(Runnable h) { allAccountsButton.setOnAction(e -> h.run()); }
    public void setOnOpenAccount(Runnable h) { openAccountButton.setOnAction(e -> h.run()); }
    public void setOnLogout(Runnable h) { logoutButton.setOnAction(e -> h.run()); }

    public void setActive(String key) {
        homeButton.getStyleClass().remove("active");
        allAccountsButton.getStyleClass().remove("active");
        openAccountButton.getStyleClass().remove("active");
        Button target = switch (key) {
            case "home" -> homeButton;
            case "accounts" -> allAccountsButton;
            case "open" -> openAccountButton;
            default -> null;
        };
        if (target != null && !target.getStyleClass().contains("active")) {
            target.getStyleClass().add("active");
        }
    }
}
