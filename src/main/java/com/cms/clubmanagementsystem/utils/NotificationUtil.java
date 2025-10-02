package com.cms.clubmanagementsystem.utils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class NotificationUtil {
    private static Popup currentPopup;

    public static void showNotification(String title, String message) {
        Platform.runLater(() -> {
            // Close existing popup if any
            if (currentPopup != null) {
                currentPopup.hide();
            }

            Popup popup = new Popup();
            currentPopup = popup;

            Label label = new Label(message);
            label.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white; -fx-padding: 15px; " +
                    "-fx-background-radius: 5; -fx-font-size: 14px; -fx-border-radius: 5;");

            StackPane content = new StackPane(label);
            content.setStyle("-fx-padding: 10px;");

            popup.getContent().add(content);

            // Get the primary stage
            Stage primaryStage = (Stage) javafx.stage.Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .findFirst()
                    .orElse(null);

            if (primaryStage != null) {
                popup.show(primaryStage);

                // Center the popup
                popup.setX(primaryStage.getX() + (primaryStage.getWidth() - label.getWidth()) / 2);
                popup.setY(primaryStage.getY() + 50);

                // Auto-close after 3 seconds
                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> popup.hide()));
                timeline.play();
            }
        });
    }
}