package com.example;

import com.example.tomasulo.config.CacheConfig;
import com.example.tomasulo.config.LatencyConfig;
import com.example.tomasulo.config.SimulationConfig;
import com.example.tomasulo.config.StationSizeConfig;
import com.example.tomasulo.core.CycleSnapshot;
import com.example.tomasulo.core.StationState;
import com.example.tomasulo.core.HazardRecord;
import com.example.tomasulo.core.InstructionStatus;
import com.example.tomasulo.core.TomasuloCore;
import com.example.tomasulo.model.Instruction;
import com.example.tomasulo.parser.InstructionParser;
import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

public class App extends Application {

    private TomasuloCore core;
    private final InstructionParser parser = new InstructionParser();
    private final ObservableList<StationState> stationRows = FXCollections.observableArrayList();
    private final ObservableList<RegRow> intRegRows = FXCollections.observableArrayList();
    private final ObservableList<RegRow> floatRegRows = FXCollections.observableArrayList();
    private final ObservableList<InstructionRow> instrRows = FXCollections.observableArrayList();
    private final ObservableList<HazardRow> hazardRows = FXCollections.observableArrayList();
    private final TextArea programArea = new TextArea();
    private final Label cycleLabel = new Label("Cycle: 0");

    @Override
    public void start(Stage stage) {
        stage.setTitle("Tomasulo Simulator");

        // Controls
        Button loadBtn = new Button("Load Program");
        Button stepBtn = new Button("Step");
        Button run10Btn = new Button("Run 10");
        Button resetBtn = new Button("Reset");

        HBox topBar = new HBox(10, loadBtn, stepBtn, run10Btn, resetBtn, cycleLabel);
        topBar.setPadding(new Insets(8));

        // Program area
        programArea.setPromptText("Enter MIPS-like instructions (e.g.\nADDI R1, R0, 5\nLW R2, 100(R1)\nADD R3, R1, R2)");
        programArea.setPrefRowCount(10);

        // Tables
        TableView<StationState> stationTable = buildStationTable();
        TableView<RegRow> intRegTable = buildRegTable("Integer Registers");
        TableView<RegRow> floatRegTable = buildRegTable("Float Registers");
        TableView<InstructionRow> instrTable = buildInstrTable();
        TableView<HazardRow> hazardTable = buildHazardTable();

        VBox left = new VBox(8, new Label("Program"), programArea);
        left.setPadding(new Insets(8));
        left.setPrefWidth(420);

        VBox right = new VBox(8, new Label("Reservation Stations"), stationTable,
                new Label("Int Registers"), intRegTable,
            new Label("Float Registers"), floatRegTable,
            new Label("Instruction Queue"), instrTable,
            new Label("Hazards"), hazardTable);
        right.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(left);
        root.setCenter(right);

        // Defaults
        initCoreWithDefaults();
        refreshTables();

        // Actions
        loadBtn.setOnAction(e -> doLoadProgram());
        stepBtn.setOnAction(e -> doStep());
        run10Btn.setOnAction(e -> { for (int i = 0; i < 10; i++) doStep(); });
        resetBtn.setOnAction(e -> { initCoreWithDefaults(); refreshTables(); });

        stage.setScene(new Scene(root, 1100, 800));
        stage.show();
    }

    private void initCoreWithDefaults() {
        // Reasonable defaults; can be made user-editable later
        LatencyConfig lat = new LatencyConfig(2, 2, 4, 8, 2, 2, 1);
        CacheConfig cache = new CacheConfig(16, 1024, 1, 10);
        StationSizeConfig sizes = new StationSizeConfig(3, 2, 2, 2, 2, 1);
        SimulationConfig cfg = new SimulationConfig(lat, cache, sizes, 16, 16);
        core = new TomasuloCore(cfg, 4096);
    }

    private TableView<StationState> buildStationTable() {
        TableView<StationState> tv = new TableView<>(stationRows);
        tv.setPrefHeight(400);
        TableColumn<StationState, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name));
        TableColumn<StationState, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().type.name()));
        TableColumn<StationState, String> cBusy = new TableColumn<>("Busy");
        cBusy.setCellValueFactory(cd -> new SimpleStringProperty(Boolean.toString(cd.getValue().busy)));
        TableColumn<StationState, String> cOp = new TableColumn<>("Opcode");
        cOp.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().opcode == null ? "" : cd.getValue().opcode.name()));
        TableColumn<StationState, String> cVj = new TableColumn<>("Vj");
        cVj.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().Vj));
        TableColumn<StationState, String> cVk = new TableColumn<>("Vk");
        cVk.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().Vk));
        TableColumn<StationState, String> cQj = new TableColumn<>("Qj");
        cQj.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().Qj));
        TableColumn<StationState, String> cQk = new TableColumn<>("Qk");
        cQk.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().Qk));
        TableColumn<StationState, Number> cAddr = new TableColumn<>("Addr");
        cAddr.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().address == null ? 0 : cd.getValue().address));
        TableColumn<StationState, Number> cRem = new TableColumn<>("Remain");
        cRem.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().remainingCycles));
        TableColumn<StationState, String> cDest = new TableColumn<>("DestReg");
        cDest.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().destRegister));
        tv.getColumns().add(cName);
        tv.getColumns().add(cType);
        tv.getColumns().add(cBusy);
        tv.getColumns().add(cOp);
        tv.getColumns().add(cVj);
        tv.getColumns().add(cVk);
        tv.getColumns().add(cQj);
        tv.getColumns().add(cQk);
        tv.getColumns().add(cAddr);
        tv.getColumns().add(cRem);
        tv.getColumns().add(cDest);
        cName.setPrefWidth(70); cType.setPrefWidth(90); cBusy.setPrefWidth(60);
        cOp.setPrefWidth(80); cVj.setPrefWidth(80); cVk.setPrefWidth(80);
        cQj.setPrefWidth(80); cQk.setPrefWidth(80); cAddr.setPrefWidth(70);
        cRem.setPrefWidth(70); cDest.setPrefWidth(90);
        return tv;
    }

    private TableView<RegRow> buildRegTable(String title) {
        TableView<RegRow> tv = new TableView<>();
        TableColumn<RegRow, String> cName = new TableColumn<>("Reg");
        cName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name));
        TableColumn<RegRow, Number> cVal = new TableColumn<>("Value");
        cVal.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().value));
        TableColumn<RegRow, String> cTag = new TableColumn<>("Tag");
        cTag.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tag));
        tv.getColumns().add(cName);
        tv.getColumns().add(cVal);
        tv.getColumns().add(cTag);
        if (title.contains("Integer")) tv.setItems(intRegRows); else tv.setItems(floatRegRows);
        tv.setPrefHeight(180);
        return tv;
    }

    private void doLoadProgram() {
        String txt = programArea.getText();
        List<String> lines = Arrays.asList(txt.split("\r?\n"));
        List<Instruction> insts;
        try {
            insts = parser.parseLines(lines);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Parse error: " + ex.getMessage()).showAndWait();
            return;
        }
        core.loadProgram(insts);
        refreshTables();
    }

    private void doStep() {
        core.stepCycle();
        refreshTables();
    }

    private void refreshTables() {
        CycleSnapshot snap = core.snapshot();
        cycleLabel.setText("Cycle: " + snap.getCycle());
        stationRows.setAll(snap.getStations());
        // Int regs
        intRegRows.clear();
        snap.getIntRegValues().keySet().stream().sorted(App::regCompare)
                .forEach(r -> intRegRows.add(new RegRow(r, snap.getIntRegValues().get(r), snap.getIntRegTags().get(r))));
        // Float regs
        floatRegRows.clear();
        snap.getFloatRegValues().keySet().stream().sorted(App::regCompare)
                .forEach(r -> floatRegRows.add(new RegRow(r, snap.getFloatRegValues().get(r), snap.getFloatRegTags().get(r))));
        // Instructions
        instrRows.clear();
        for (InstructionStatus is : snap.getInstructionQueue()) {
            instrRows.add(new InstructionRow(is.getPcIndex(), is.getText(), is.getStage().name()));
        }
        // Hazards
        hazardRows.clear();
        for (HazardRecord hr : snap.getHazards()) {
            hazardRows.add(new HazardRow(hr.getCycle(), hr.getType().name(), hr.getCausingStation(), hr.getAffectedStation(), hr.getRegister()));
        }
    }

    private static int regCompare(String a, String b) {
        // sort by prefix then number
        if (a.charAt(0) != b.charAt(0)) return Character.compare(a.charAt(0), b.charAt(0));
        try {
            int ia = Integer.parseInt(a.substring(1));
            int ib = Integer.parseInt(b.substring(1));
            return Integer.compare(ia, ib);
        } catch (Exception e) { return a.compareTo(b); }
    }

    public static class RegRow {
        public final String name;
        public final int value;
        public final String tag;
        public RegRow(String name, Integer value, String tag) {
            this.name = name;
            this.value = value == null ? 0 : value;
            this.tag = tag;
        }
    }

    private TableView<InstructionRow> buildInstrTable() {
        TableView<InstructionRow> tv = new TableView<>(instrRows);
        TableColumn<InstructionRow, Number> cPc = new TableColumn<>("PC");
        cPc.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().pc));
        TableColumn<InstructionRow, String> cText = new TableColumn<>("Text");
        cText.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().text));
        TableColumn<InstructionRow, String> cStage = new TableColumn<>("Stage");
        cStage.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().stage));
        tv.getColumns().add(cPc);
        tv.getColumns().add(cText);
        tv.getColumns().add(cStage);
        tv.setPrefHeight(200);
        return tv;
    }

    private TableView<HazardRow> buildHazardTable() {
        TableView<HazardRow> tv = new TableView<>(hazardRows);
        TableColumn<HazardRow, Number> cCycle = new TableColumn<>("Cycle");
        cCycle.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().cycle));
        TableColumn<HazardRow, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().type));
        TableColumn<HazardRow, String> cCause = new TableColumn<>("Cause");
        cCause.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().causing));
        TableColumn<HazardRow, String> cAff = new TableColumn<>("Affected");
        cAff.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().affected));
        TableColumn<HazardRow, String> cReg = new TableColumn<>("Register");
        cReg.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reg));
        tv.getColumns().add(cCycle);
        tv.getColumns().add(cType);
        tv.getColumns().add(cCause);
        tv.getColumns().add(cAff);
        tv.getColumns().add(cReg);
        tv.setPrefHeight(200);
        return tv;
    }

    public static class InstructionRow {
        public final int pc;
        public final String text;
        public final String stage;
        public InstructionRow(int pc, String text, String stage) {
            this.pc = pc; this.text = text; this.stage = stage;
        }
    }

    public static class HazardRow {
        public final int cycle; public final String type; public final String causing; public final String affected; public final String reg;
        public HazardRow(int cycle, String type, String causing, String affected, String reg) {
            this.cycle = cycle; this.type = type; this.causing = causing; this.affected = affected; this.reg = reg;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
