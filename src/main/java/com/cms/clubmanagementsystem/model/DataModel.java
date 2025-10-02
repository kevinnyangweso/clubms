package com.cms.clubmanagementsystem.model;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.UUID;

/**
 * Central data model for the application that holds observable data
 * that can be accessed and updated from any controller.
 */
public class DataModel {
    private static DataModel instance;
    private final ObservableList<Learner> allLearners = FXCollections.observableArrayList();

    private DataModel() {
        // Private constructor for singleton
    }

    public static synchronized DataModel getInstance() {
        if (instance == null) {
            instance = new DataModel();
        }
        return instance;
    }

    public ObservableList<Learner> getAllLearners() {
        return allLearners;
    }

    public void updateLearners(List<Learner> updatedLearners) {
        Platform.runLater(() -> {
            allLearners.clear();
            allLearners.addAll(updatedLearners);
            System.out.println("DataModel: Updated learners list with " + updatedLearners.size() + " learners");
        });
    }

    public void clearData() {
        Platform.runLater(() -> {
            allLearners.clear();
        });
    }
}