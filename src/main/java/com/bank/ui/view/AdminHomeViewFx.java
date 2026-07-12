package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AdminHomeViewFx implements AdminHomeView {

    private final Label titleLabel = new Label("Dashboard — Home");
    private final Label summaryLabel = new Label();
    private final VBox root = new VBox(12);

    public AdminHomeViewFx() {
        root.setPadding(new Insets(24));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        root.getChildren().addAll(titleLabel, summaryLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showSummary(String summary) { summaryLabel.setText(summary); }
}
