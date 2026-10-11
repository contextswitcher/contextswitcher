package com.contextswitcher.ui;

import java.util.List;
import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import atlantafx.base.theme.Styles;

import com.contextswitcher.config.HostSettings;

import org.jspecify.annotations.Nullable;

import tools.maran.extendedtable.tableview.ExtendedTableView;
import tools.maran.extendedtable.tableview.column.CheckBoxTableColumn;
import tools.maran.extendedtable.tableview.column.StringTableColumn;

/// The settings dialog's host table (`dsn~host-table~1`): one row per machine
/// sessions run on — its ssh destination, whether it is switched on, where new
/// workspaces roots go there, and its RefactoringMiner directory — edited in
/// place (ExtendedTable, MADR 0038). *Add* appends an empty row to type into,
/// *Remove* drops the selected one, and *Set up RefactoringMiner* installs the
/// release on the selected host and fills its cell — the per-host successor of
/// the one global directory's **Set up** button (`dsn~refactoring-miner-setup~1`).
// [impl->dsn~host-table~1]
final class HostTable extends VBox {

    /// One editable row; turned into a [HostSettings] when the form is read.
    static final class Row {
        String destination;
        boolean active;
        @Nullable String workspaces;
        @Nullable String refactoringMiner;

        Row(HostSettings host) {
            this.destination = host.destination();
            this.active = host.active();
            this.workspaces = host.workspaces();
            this.refactoringMiner = host.refactoringMiner();
        }

        HostSettings toHost() {
            return new HostSettings(destination, active, workspaces, refactoringMiner);
        }
    }

    private final ExtendedTableView<Row> table = new ExtendedTableView<>();

    HostTable(List<HostSettings> hosts, MainWindow.RefactoringMinerInstaller installer) {
        super(4);
        table.setId("host-table");
        table.setEditable(true);
        table.setValidationEnabled(true);
        table.setShowHeader(false);

        StringTableColumn<Row> destination = new StringTableColumn<>("Destination");
        destination.setReadFunction(row -> row.destination);
        destination.setWriteFunction((row, text) -> row.destination = text == null ? "" : text.strip());
        destination.setValidator(text -> text != null && !text.isBlank());
        destination.setPrefWidth(150);

        CheckBoxTableColumn<Row> active = new CheckBoxTableColumn<>("Active");
        active.setReadFunction(row -> row.active);
        active.setWriteFunction((row, on) -> row.active = Boolean.TRUE.equals(on));
        active.setPrefWidth(60);

        StringTableColumn<Row> workspaces = new StringTableColumn<>("Workspaces");
        workspaces.setReadFunction(row -> row.workspaces);
        workspaces.setWriteFunction((row, text) -> row.workspaces = text);
        workspaces.setPrefWidth(190);

        StringTableColumn<Row> refactoringMiner = new StringTableColumn<>("RefactoringMiner");
        refactoringMiner.setReadFunction(row -> row.refactoringMiner);
        refactoringMiner.setWriteFunction((row, text) -> row.refactoringMiner = text);
        refactoringMiner.setPrefWidth(240);

        table.getColumns().add(destination);
        table.getColumns().add(active);
        table.getColumns().add(workspaces);
        table.getColumns().add(refactoringMiner);
        table.setItems(FXCollections.observableArrayList(hosts.stream().map(Row::new).toList()));
        // Room for a handful of hosts without the form growing a second
        // scrollbar; more scroll inside the table.
        table.setPrefHeight(150);

        Button add = new Button("Add");
        add.setId("host-add");
        add.getStyleClass().add(Styles.SMALL);
        add.setOnAction(event -> {
            Row row = new Row(HostSettings.of(""));
            table.getBackingItems().add(row);
            table.getSelectionModel().select(row);
            table.edit(table.getItems().indexOf(row), destination);
        });
        Button remove = new Button("Remove");
        remove.setId("host-remove");
        remove.getStyleClass().add(Styles.SMALL);
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            Row row = table.getSelectionModel().getSelectedItem();
            if (row != null) {
                table.getBackingItems().remove(row);
            }
        });
        Button setup = new Button("Set up RefactoringMiner");
        setup.getStyleClass().add(Styles.SMALL);
        setup.setTooltip(new Tooltip("Download and unzip RefactoringMiner on the selected host, "
                + "then fill in its directory."));
        setup.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        setup.setOnAction(event -> {
            Row row = table.getSelectionModel().getSelectedItem();
            if (row == null || row.destination.isBlank()) {
                new Alert(Alert.AlertType.INFORMATION, "Select a host with a destination first.").show();
                return;
            }
            // Disabled for the round-trip (the async single-shot convention);
            // the status bar carries the outcome, the value still needs a Save.
            setup.disableProperty().unbind();
            setup.setDisable(true);
            installer.install(List.of(row.destination), installed -> {
                setup.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                if (installed != null) {
                    row.refactoringMiner = installed;
                    table.refresh();
                }
            });
        });
        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(6, add, remove, setup);
        getChildren().addAll(table, buttons);
    }

    /// The rows as hosts, in table order; a row left without a destination is
    /// dropped (an *Add* nobody typed into).
    List<HostSettings> hosts() {
        return table.getBackingItems().stream()
                .filter(row -> row.destination != null && !row.destination.isBlank())
                .map(Row::toHost)
                .filter(Objects::nonNull)
                .toList();
    }
}
