package com.example;

import com.example.tomasulo.config.CacheConfig;
import com.example.tomasulo.config.LatencyConfig;
import com.example.tomasulo.config.SimulationConfig;
import com.example.tomasulo.config.StationSizeConfig;
import com.example.tomasulo.core.CycleSnapshot;
import com.example.tomasulo.core.StationState;
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
    // User-provided register initialization
            private Map<String, Integer> userIntRegInit = new HashMap<>();
            private Map<String, Integer> userFloatRegInit = new HashMap<>();
    // User-configurable station/buffer sizes
    private int userFpAddStations = 3;
    private int userFpMulStations = 2;
    private int userIntStations = 2;
    private int userLoadBuffers = 2;
    private int userStoreBuffers = 1;
    private int userBranchStations = 1;
// User-configurable cache parameters
private int userHitLatency = 1;
private int userMissPenalty = 2;
private int userBlockSize = 8;
private int userCacheSize = 128;

// User-configurable instruction latencies
private int userAddLatency = 2;
private int userSubLatency = 2;
private int userMulLatency = 10;
private int userDivLatency = 10;
private int userBranchLatency = 1;
private int userStoreLatency = 1;
private int userLoadLatency = 2;
    private TomasuloCore core;
    private final InstructionParser parser = new InstructionParser();
    private final ObservableList<StationState> stationRows = FXCollections.observableArrayList();
    private final ObservableList<RegRow> intRegRows = FXCollections.observableArrayList();
    private final ObservableList<RegRow> floatRegRows = FXCollections.observableArrayList();
    private final ObservableList<InstructionRow> instrRows = FXCollections.observableArrayList();
    private final ObservableList<CacheRow> cacheRows = FXCollections.observableArrayList();
    private final TextArea programArea = new TextArea();
    private final Label cycleLabel = new Label("Cycle: 0");

    @Override
    public void start(Stage stage) {
        // Help menu for cache/memory documentation
        MenuBar menuBar = new MenuBar();
                        Menu helpMenu = new Menu("Help");
                        MenuItem cacheHelp = new MenuItem("Cache Addressing Info");
                        helpMenu.getItems().add(cacheHelp);
                        menuBar.getMenus().add(helpMenu);

                        cacheHelp.setOnAction(e -> showCacheHelpDialog());
                    // Prompt user for register initialization
                    showRegisterInitDialog();
                // Prompt user for station/buffer sizes
                userFpAddStations = getUserInt("FP Add Reservation Stations", 3);
                userFpMulStations = getUserInt("FP Mul Reservation Stations", 2);
                userIntStations = getUserInt("Integer Reservation Stations", 2);
                userLoadBuffers = getUserInt("Load Buffers", 2);
                userStoreBuffers = getUserInt("Store Buffers", 1);
                userBranchStations = getUserInt("Branch Reservation Stations", 1);
                System.out.println("[CONFIG] Reservation Station Sizes:");
                System.out.println("[CONFIG]   FP Add Stations: " + userFpAddStations);
                System.out.println("[CONFIG]   FP Mul Stations: " + userFpMulStations);
                System.out.println("[CONFIG]   Integer Stations: " + userIntStations);
                System.out.println("[CONFIG]   Load Buffers: " + userLoadBuffers);
                System.out.println("[CONFIG]   Store Buffers: " + userStoreBuffers);
                System.out.println("[CONFIG]   Branch Stations: " + userBranchStations);
        // Prompt user for instruction latencies
        userAddLatency = getUserInt("ADD Latency (cycles)", 2);
        userSubLatency = getUserInt("SUB Latency (cycles)", 2);
        userMulLatency = getUserInt("MUL Latency (cycles)", 10);
        userDivLatency = getUserInt("DIV Latency (cycles)", 10);
        userLoadLatency = getUserInt("LOAD Latency (cycles)", 2);
        userBranchLatency = getUserInt("BRANCH Latency (cycles)", 1);
        userStoreLatency = getUserInt("STORE Latency (cycles)", 1);
        System.out.println("[CONFIG] Instruction Latencies:");
        System.out.println("[CONFIG]   ADD/SUB: " + userAddLatency + " cycles");
        System.out.println("[CONFIG]   MUL: " + userMulLatency + " cycles");
        System.out.println("[CONFIG]   DIV: " + userDivLatency + " cycles");
        System.out.println("[CONFIG]   LOAD: " + userLoadLatency + " cycles");
        System.out.println("[CONFIG]   BRANCH: " + userBranchLatency + " cycles");
        System.out.println("[CONFIG]   STORE: " + userStoreLatency + " cycles");
    stage.setTitle("Tomasulo Simulator");        // Prompt user for cache parameters
        userBlockSize = getUserInt("Cache Block Size (bytes)", 8);
        userCacheSize = getUserInt("Cache Size (bytes)", 128);
        userHitLatency = getUserInt("Cache Hit Latency (cycles)", 1);
        userMissPenalty = getUserInt("Cache Miss Penalty (cycles)", 2);
        System.out.println("[CONFIG] Cache Configuration:");
        System.out.println("[CONFIG]   Block Size: " + userBlockSize + " bytes");
        System.out.println("[CONFIG]   Cache Size: " + userCacheSize + " bytes");
        System.out.println("[CONFIG]   Hit Latency: " + userHitLatency + " cycles");
        System.out.println("[CONFIG]   Miss Penalty: " + userMissPenalty + " cycles");
        
        // Calculate register size based on cache block size
        int registerSizeBytes = userBlockSize;
        System.out.println("[CONFIG] Register Configuration:");
        System.out.println("[CONFIG]   Register Size: " + registerSizeBytes + " bytes (matched to cache block size)");
        System.out.println("[CONFIG]   This means each register can hold " + (registerSizeBytes / 4) + " 32-bit word(s)");

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
        TableView<CacheRow> cacheTable = buildCacheTable();

        // Register tables side by side under program area
        VBox programBox = new VBox(4, new Label("Program"), programArea);
        programBox.setPadding(new Insets(8));
        programBox.setPrefWidth(420);

        HBox regTables = new HBox(8,
            new VBox(new Label("Int Registers"), intRegTable),
            new VBox(new Label("Float Registers"), floatRegTable)
        );
        regTables.setPadding(new Insets(8, 0, 0, 0));

        VBox left = new VBox(8, programBox, regTables);
        left.setPadding(new Insets(8));
        left.setPrefWidth(420);

        VBox right = new VBox(8, new Label("Reservation Stations"), stationTable,
            new Label("Instruction Queue"), instrTable,
            new Label("Data Cache"), cacheTable);
        right.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        VBox topVBox = new VBox(menuBar, topBar);
        root.setTop(topVBox);
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

        stage.setScene(new Scene(root, 1200, 750));
        stage.show();
    }

    private void initCoreWithDefaults() {
        // Reasonable defaults; can be made user-editable later
        LatencyConfig lat = new LatencyConfig(
            userAddLatency,
            userSubLatency,
            userMulLatency,
            userDivLatency,
            userBranchLatency,
            userStoreLatency,
            userLoadLatency
        );
        // Use user-provided cache parameters
        CacheConfig cache = new CacheConfig(userBlockSize, userCacheSize, userHitLatency, userMissPenalty);
        StationSizeConfig sizes = new StationSizeConfig(
            userFpAddStations,
            userFpMulStations,
            userIntStations,
            userLoadBuffers,
            userStoreBuffers,
            userBranchStations
        );
        // Calculate number of registers based on cache block size
        // For 4-byte block: 32 registers, for 8-byte block: 32 registers (keeping count same, size differs)
        int registerCount = 32;
        System.out.println("[CONFIG] Initializing " + registerCount + " integer and " + registerCount + " float registers");
        SimulationConfig cfg = new SimulationConfig(lat, cache, sizes, registerCount, registerCount);
        core = new TomasuloCore(cfg, 4096);
        // Apply user register initialization
        userIntRegInit.forEach((reg, val) -> core.getIntRegisters().init(reg, val));
        userFloatRegInit.forEach((reg, val) -> core.getFloatRegisters().init(reg, val));
        
        // Preload memory with test values at addresses accessed by test program
        com.example.tomasulo.memory.Memory memory = core.getMemory();
        memory.initWord(100, 1800000);  // Memory[100] = 1800000 (writes bytes at 100-103: 00 1B 77 40 in big-endian)
        memory.initWord(104, 5);   // Memory[104] = 5 (writes bytes at 104-107: 00 00 00 05 in big-endian)
        memory.initWord(120, 25);  // Memory[120] for L.D F2, 20(R2) where R2=100
        System.out.println("[CONFIG] Memory Preloaded:");
        System.out.println("[CONFIG]   Address 100: " + memory.loadWordRaw(100));
        System.out.println("[CONFIG]   Address 104: " + memory.loadWordRaw(104));
        System.out.println("[CONFIG]   Address 120: " + memory.loadWordRaw(120));
        System.out.println("[CONFIG] With these values, your test program should compute:");
        System.out.println("[CONFIG]   F6 = 10 (from address 100)");
        System.out.println("[CONFIG]   F2 = 25 (from address 120)");
        System.out.println("[CONFIG]   F7 = F1 + F3 = 2 + 3 = 5");
        System.out.println("[CONFIG]   F0 = F2 * F4 = 25 * 5 = 125");
        System.out.println("[CONFIG]   F8 = F2 - F6 = 25 - 10 = 15");
        System.out.println("[CONFIG]   F10 = F0 / F6 = 125 / 10 = 12");
        System.out.println("[CONFIG]   Memory[100] = F10 = 12 (after S.D)");
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
        tv.setPrefHeight(550);  // Increased to show all 32 registers
        cName.setPrefWidth(40);
        cVal.setPrefWidth(60);
        cTag.setPrefWidth(60);
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
            instrRows.add(new InstructionRow(is.getPcIndex(), is.getText(), is.getStage().name(), 
                is.getIssueCycle(), is.getExecuteStartCycle(), is.getExecuteEndCycle(), is.getCommitCycle()));
        }
        // Cache
        cacheRows.clear();
        if (core != null) {
            java.util.Map<Integer, byte[]> cacheBlocks = core.getDataCache().getBlocks();
            int blockSize = core.getDataCache().getConfig().getBlockSizeBytes();
            int cacheSize = core.getDataCache().getConfig().getCacheSizeBytes();
            int numSlots = cacheSize / blockSize;
            
            System.out.println("[UI] Updating cache display - found " + cacheBlocks.size() + " blocks in cache");
            for (java.util.Map.Entry<Integer, byte[]> entry : cacheBlocks.entrySet()) {
                int blockAddress = entry.getKey();
                int cacheSlot = (blockAddress / blockSize) % numSlots;
                
                StringBuilder hex = new StringBuilder();
                for (byte b : entry.getValue()) {
                    hex.append(String.format("%02X ", b & 0xFF));
                }
                System.out.println("[UI] Adding cache row - Slot: " + cacheSlot + ", BlockAddr: " + blockAddress + ", Data: " + hex.toString().trim());
                cacheRows.add(new CacheRow(cacheSlot, blockAddress, hex.toString().trim()));
            }
            // Sort by cache slot number
            cacheRows.sort((a, b) -> Integer.compare(a.cacheSlot, b.cacheSlot));
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
        TableColumn<InstructionRow, String> cText = new TableColumn<>("Instruction");
        cText.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().text));
        TableColumn<InstructionRow, String> cStage = new TableColumn<>("Stage");
        cStage.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().stage));
        TableColumn<InstructionRow, String> cIssue = new TableColumn<>("Issue");
        cIssue.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().issueCycle == -1 ? "" : String.valueOf(cd.getValue().issueCycle)));
        TableColumn<InstructionRow, String> cExecStart = new TableColumn<>("Exec Start");
        cExecStart.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().executeStartCycle == -1 ? "" : String.valueOf(cd.getValue().executeStartCycle)));
        TableColumn<InstructionRow, String> cExecEnd = new TableColumn<>("Exec End");
        cExecEnd.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().executeEndCycle == -1 ? "" : String.valueOf(cd.getValue().executeEndCycle)));
        TableColumn<InstructionRow, String> cCommit = new TableColumn<>("Commit");
        cCommit.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().commitCycle == -1 ? "" : String.valueOf(cd.getValue().commitCycle)));
        tv.getColumns().add(cPc);
        tv.getColumns().add(cText);
        tv.getColumns().add(cStage);
        tv.getColumns().add(cIssue);
        tv.getColumns().add(cExecStart);
        tv.getColumns().add(cExecEnd);
        tv.getColumns().add(cCommit);
        cPc.setPrefWidth(40);
        cText.setPrefWidth(180);
        cStage.setPrefWidth(90);
        cIssue.setPrefWidth(60);
        cExecStart.setPrefWidth(80);
        cExecEnd.setPrefWidth(80);
        cCommit.setPrefWidth(60);
        tv.setPrefHeight(200);
        return tv;
    }

    public static class InstructionRow {
        public final int pc;
        public final String text;
        public final String stage;
        public final int issueCycle;
        public final int executeStartCycle;
        public final int executeEndCycle;
        public final int commitCycle;
        public InstructionRow(int pc, String text, String stage, int issueCycle, int executeStartCycle, int executeEndCycle, int commitCycle) {
            this.pc = pc; 
            this.text = text; 
            this.stage = stage;
            this.issueCycle = issueCycle;
            this.executeStartCycle = executeStartCycle;
            this.executeEndCycle = executeEndCycle;
            this.commitCycle = commitCycle;
        }
    }

    public static class CacheRow {
        public final int cacheSlot;
        public final int blockAddress;
        public final String data;
        public CacheRow(int cacheSlot, int blockAddress, String data) {
            this.cacheSlot = cacheSlot;
            this.blockAddress = blockAddress;
            this.data = data;
        }
    }

    private TableView<CacheRow> buildCacheTable() {
        TableView<CacheRow> tv = new TableView<>(cacheRows);
        TableColumn<CacheRow, Number> cSlot = new TableColumn<>("Cache Slot");
        cSlot.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().cacheSlot));
        TableColumn<CacheRow, Number> cBlock = new TableColumn<>("Memory Block Addr");
        cBlock.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().blockAddress));
        TableColumn<CacheRow, String> cData = new TableColumn<>("Data (Hex)");
        cData.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().data));
        tv.getColumns().add(cSlot);
        tv.getColumns().add(cBlock);
        tv.getColumns().add(cData);
        cSlot.setPrefWidth(80);
        cBlock.setPrefWidth(130);
        cData.setPrefWidth(280);
        tv.setPrefHeight(200);
        return tv;
    }

    public static void main(String[] args) {
        launch();
    }

    // Show dialog explaining cache addressing and memory mapping
    private void showCacheHelpDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cache Addressing and Memory Mapping");
        alert.setHeaderText("How Data Cache Addressing Works");
        alert.setContentText(
            "- The data cache is direct-mapped.\n" +
            "- Memory is byte-addressable (each address points to 1 byte).\n" +
            "- Each cache block holds 'block size' bytes.\n" +
            "- The block address for any memory address is: blockAddress = address - (address % blockSize).\n" +
            "- On a cache access, if the block containing the address is not present, it is fetched from memory (compulsory miss).\n" +
            "- If the block is present, it is a hit.\n" +
            "- Loads and stores operate on 4 bytes (a word) at a time.\n" +
            "- When loading a word (e.g., LW R1, 100(R2)):\n" +
            "    1. Compute the effective address (e.g., 100 + R2).\n" +
            "    2. Compute the block address: blockAddress = effectiveAddress - (effectiveAddress % blockSize).\n" +
            "    3. If the block is in the cache, read the 4 bytes (word) from the block (hit).\n" +
            "    4. If the block is not in the cache, fetch the block from memory, place it in the cache, then read the 4 bytes (miss).\n" +
            "- Example: For address 100, block size 16, blockAddress = 100 - (100 % 16) = 96.\n" +
            "- The cache is sized by user input (total bytes and block size).\n" +
            "- Write-through policy is used for stores.\n");
        alert.showAndWait();
    }

    // Show dialog for user to preload register values
    private void showRegisterInitDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register Initialization");
        dialog.setHeaderText("Preload Integer and Float Registers\n(Note: R0 is hardwired to 0 and cannot be changed)\nFormat: R1=5, F2=3, ...");
        TextArea regArea = new TextArea("R2=100, F1=2, F3=3, F4=5");
        regArea.setPromptText("R1=10, R2=20, F0=3, F1=0 ...");
        regArea.setPrefRowCount(4);
        dialog.getDialogPane().setContent(regArea);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(dialogButton -> dialogButton);
        Optional<ButtonType> result = dialog.showAndWait();
        userIntRegInit.clear();
        userFloatRegInit.clear();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String[] entries = regArea.getText().split(",");
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty() || !trimmed.contains("=")) continue;
                String[] parts = trimmed.split("=");
                if (parts.length != 2) continue;
                String reg = parts[0].trim().toUpperCase();
                try {
                    int val = Integer.parseInt(parts[1].trim());
                    // R0 is hardwired to 0 and cannot be initialized
                    if (reg.equals("R0")) {
                        System.out.println("[CONFIG] Warning: R0 is hardwired to 0 and cannot be changed. Ignoring initialization.");
                        continue;
                    }
                    if (reg.startsWith("R")) userIntRegInit.put(reg, val);
                    else if (reg.startsWith("F")) userFloatRegInit.put(reg, val);
                } catch (Exception ignored) {}
            }
        }
    }
    // Helper to prompt user for integer input with default
    private int getUserInt(String prompt, int defaultValue) {
        TextInputDialog dialog = new TextInputDialog(Integer.toString(defaultValue));
        dialog.setTitle("Parameter Input");
        dialog.setHeaderText(prompt);
        dialog.setContentText(prompt + ":");
        while (true) {
            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                try {
                    return Integer.parseInt(result.get().trim());
                } catch (Exception ignored) {}
            } else {
                return defaultValue;
            }
        }
    }
}
