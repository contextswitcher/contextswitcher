package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.contextswitcher.tasks.TaskSync;

/// The window behind the toolbar's sync-groups button: the configured groups
/// (add/remove) and the shared tasks waiting to be sorted in — right-click one
/// to move it into a category or to ignore it until it changes. FX thread.
// [impl->dsn~task-sync-groups-ui~1]
final class SyncGroupsWindow {

    private final Window owner;
    private final TaskSync sync;
    private final Supplier<List<String>> categories;
    private final BiConsumer<TaskSync.Incoming, String> onSortIn;
    private final Consumer<TaskSync.Incoming> onIgnore;
    private final Runnable round;

    private @Nullable Stage stage;
    private final ListView<TaskSync.Group> groupList = new ListView<>();
    private final ListView<TaskSync.Incoming> taskList = new ListView<>();
    private final CheckBox showIgnored = new CheckBox("Show ignored");
    private final Button syncNow = new Button("Sync now");
    private List<TaskSync.Incoming> incoming = List.of();

    SyncGroupsWindow(Window owner, TaskSync sync, Supplier<List<String>> categories,
            BiConsumer<TaskSync.Incoming, String> onSortIn, Consumer<TaskSync.Incoming> onIgnore,
            Runnable round) {
        this.owner = owner;
        this.sync = sync;
        this.categories = categories;
        this.onSortIn = onSortIn;
        this.onIgnore = onIgnore;
        this.round = round;
    }

    void show() {
        Stage open = stage;
        if (open != null) {
            open.toFront();
            return;
        }
        Stage window = new Stage();
        window.initOwner(owner);
        window.setTitle("Sync groups");
        window.setScene(new Scene(content(), 560, 480));
        window.setOnHidden(event -> stage = null);
        stage = window;
        groupList.getItems().setAll(sync.groups());
        render();
        window.show();
    }

    /// A round finished (or the list changed locally): re-render the tasks
    /// and re-enable "Sync now".
    void update(List<TaskSync.Incoming> incoming) {
        this.incoming = incoming;
        syncNow.setDisable(false);
        if (stage != null) {
            groupList.getItems().setAll(sync.groups());
            render();
        }
    }

    private Region content() {
        groupList.setPrefHeight(110);
        groupList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(TaskSync.@Nullable Group group, boolean empty) {
                super.updateItem(group, empty);
                setText(empty || group == null ? null : group.name() + " — " + group.url());
            }
        });
        Button add = new Button("Add…");
        add.getStyleClass().add(Styles.SMALL);
        add.setOnAction(event -> addGroup());
        Button remove = new Button("Remove");
        remove.getStyleClass().add(Styles.SMALL);
        remove.disableProperty().bind(groupList.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> removeGroup(groupList.getSelectionModel().getSelectedItem()));
        HBox groupButtons = new HBox(8, add, remove);

        taskList.setPlaceholder(new Label("No shared tasks waiting to be sorted in."));
        taskList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(TaskSync.@Nullable Incoming task, boolean empty) {
                super.updateItem(task, empty);
                getStyleClass().remove(Styles.TEXT_MUTED);
                if (empty || task == null) {
                    setText(null);
                    setContextMenu(null);
                    return;
                }
                setText(task.title() + "  (" + task.group() + (task.ignored() ? ", ignored" : "") + ")");
                if (task.ignored()) {
                    getStyleClass().add(Styles.TEXT_MUTED);
                }
                setContextMenu(taskMenu(task));
            }
        });
        showIgnored.setOnAction(event -> render());
        Label tasksLabel = new Label("Shared tasks to sort in — right-click to move one into a category");
        tasksLabel.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox tasksHeader = new HBox(8, tasksLabel, spacer, showIgnored);
        tasksHeader.setAlignment(Pos.CENTER_LEFT);

        syncNow.getStyleClass().add(Styles.SMALL);
        syncNow.setOnAction(event -> {
            syncNow.setDisable(true);
            round.run();
        });
        Button close = new Button("Close");
        close.getStyleClass().add(Styles.SMALL);
        close.setCancelButton(true);
        close.setOnAction(event -> {
            Stage open = stage;
            if (open != null) {
                open.close();
            }
        });
        HBox bottom = new HBox(8, syncNow, close);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        VBox.setVgrow(taskList, Priority.ALWAYS);
        VBox root = new VBox(8, new Label("Groups"), groupList, groupButtons, tasksHeader, taskList, bottom);
        root.setPadding(new Insets(12, 16, 12, 16));
        return root;
    }

    private void render() {
        taskList.getItems().setAll(incoming.stream()
                .filter(task -> showIgnored.isSelected() || !task.ignored())
                .toList());
    }

    private ContextMenu taskMenu(TaskSync.Incoming task) {
        Menu move = new Menu("Move to category");
        MenuItem root = new MenuItem("(no category)");
        root.setOnAction(event -> onSortIn.accept(task, ""));
        move.getItems().add(root);
        for (String category : categories.get()) {
            MenuItem item = new MenuItem(category);
            item.setOnAction(event -> onSortIn.accept(task, category));
            move.getItems().add(item);
        }
        MenuItem ignore = new MenuItem("Ignore until it changes");
        ignore.setDisable(task.ignored());
        ignore.setOnAction(event -> onIgnore.accept(task));
        return new ContextMenu(move, ignore);
    }

    private void addGroup() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Add sync group");
        dialog.setHeaderText("A git repository shared with the other members (e.g. git@github.com:org/tasks.git).");
        TextField name = new TextField();
        name.setPromptText("team");
        TextField url = new TextField();
        url.setPromptText("git@github.com:org/tasks.git");
        GridPane grid = new GridPane(8, 8);
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("Repository"), url);
        GridPane.setHgrow(url, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !TaskSync.validName(name.getText().strip()) || url.getText().isBlank()
                        || sync.groups().stream().anyMatch(group -> group.name().equals(name.getText().strip())),
                name.textProperty(), url.textProperty()));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        List<TaskSync.Group> groups = new ArrayList<>(sync.groups());
        groups.add(new TaskSync.Group(name.getText().strip(), url.getText().strip()));
        sync.saveGroups(groups);
        groupList.getItems().setAll(groups);
        syncNow.setDisable(true);
        round.run();
    }

    private void removeGroup(TaskSync.@Nullable Group group) {
        if (group == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Stop syncing \"%s\"? Its tasks stay in your task list; they just no longer sync."
                        .formatted(group.name()));
        confirm.initOwner(stage);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        List<TaskSync.Group> groups = new ArrayList<>(sync.groups());
        groups.remove(group);
        sync.saveGroups(groups);
        groupList.getItems().setAll(groups);
        update(incoming.stream().filter(task -> !task.group().equals(group.name())).toList());
    }
}
