package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class RoleSelectViewFx {

    private final Button customerButton = new Button("Customer");
    private final Button adminButton = new Button("Admin");
    private final VBox root = new VBox(10);

    public RoleSelectViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Welcome — choose a role"), customerButton, adminButton);
    }

    public Parent getRoot() { return root; }

    public void setOnCustomer(Runnable handler) { customerButton.setOnAction(e -> handler.run()); }
    public void setOnAdmin(Runnable handler) { adminButton.setOnAction(e -> handler.run()); }
}
