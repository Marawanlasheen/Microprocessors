package com.example.tomasulo.core;

import com.example.tomasulo.config.SimulationConfig;
import com.example.tomasulo.model.*;
import com.example.tomasulo.memory.*;

import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;

public class TomasuloCore {
    private final SimulationConfig config;
    private final RegisterFile intRegisters = new RegisterFile();
    private final RegisterFile floatRegisters = new RegisterFile();
    private final ReservationStations stations = new ReservationStations();
    private final DataCache dataCache;
    private final Memory memory;
    private final CommonDataBus cdb = new CommonDataBus();
    private final List<Instruction> program = new ArrayList<>();
    private final List<InstructionStatus.Stage> instStages = new ArrayList<>();
    private int pcIndex = 0;
    private int cycle = 0;
    private final List<HazardRecord> hazardLog = new ArrayList<>();

    public TomasuloCore(SimulationConfig config, int memorySizeBytes) {
        this.config = config;
        this.memory = new Memory(memorySizeBytes);
        this.dataCache = new DataCache(config.getCacheConfig());
        allocateStations();
        initRegisters();
    }

    private void allocateStations() {
        for (int i = 0; i < config.getStationSizeConfig().getFpAddStations(); i++) {
            stations.add(new ReservationStationEntry("A"+i, ReservationStationType.FP_ADD));
        }
        for (int i = 0; i < config.getStationSizeConfig().getFpMulStations(); i++) {
            stations.add(new ReservationStationEntry("M"+i, ReservationStationType.FP_MUL));
        }
        // Split integer stations into INT_ADD and INT_SUB for granularity
        int intAddCount = config.getStationSizeConfig().getIntStations() / 2;
        int intSubCount = config.getStationSizeConfig().getIntStations() - intAddCount;
        for (int i = 0; i < intAddCount; i++) {
            stations.add(new ReservationStationEntry("IA"+i, ReservationStationType.INT_ADD));
        }
        for (int i = 0; i < intSubCount; i++) {
            stations.add(new ReservationStationEntry("IS"+i, ReservationStationType.INT_SUB));
        }
        for (int i = 0; i < config.getStationSizeConfig().getLoadBuffers(); i++) {
            stations.add(new ReservationStationEntry("L"+i, ReservationStationType.LOAD));
        }
        for (int i = 0; i < config.getStationSizeConfig().getStoreBuffers(); i++) {
            stations.add(new ReservationStationEntry("S"+i, ReservationStationType.STORE));
        }
        for (int i = 0; i < config.getStationSizeConfig().getBranchStations(); i++) {
            stations.add(new ReservationStationEntry("B"+i, ReservationStationType.BRANCH));
        }
    }

    private void initRegisters() {
        for (int i = 0; i < config.getIntegerRegisterCount(); i++) {
            intRegisters.init("R"+i, 0);
        }
        for (int i = 0; i < config.getFloatRegisterCount(); i++) {
            floatRegisters.init("F"+i, 0);
        }
    }

    public void loadProgram(List<Instruction> instructions) {
        program.clear();
        program.addAll(instructions);
        pcIndex = 0;
        cycle = 0;
        instStages.clear();
        for (int i = 0; i < program.size(); i++) instStages.add(InstructionStatus.Stage.QUEUED);
    }

    public int getCycle() { return cycle; }

    public void stepCycle() {
        cycle++;
        writeBackPhase();
        executePhase();
        issuePhase();
    }

    private void issuePhase() {
        if (pcIndex >= program.size()) return;
        // Simple control: do not issue past an unresolved branch
        boolean unresolvedBranch = stations.busyEntries().stream().anyMatch(e -> e.getType() == ReservationStationType.BRANCH);
        if (unresolvedBranch) return;
        Instruction inst = program.get(pcIndex);
        ReservationStationEntry entry = mapInstructionToStation(inst);
        if (entry == null) return; // structural stall
        // fill station (simplified: immediate capture of operands/tags)
        entry.setBusy(true);
        entry.setOpcode(inst.getOpcode());
        entry.setDestRegister(inst.getDest());
        entry.setSrc1Register(inst.getSrc1());
        entry.setSrc2Register(inst.getSrc2());
        entry.setImmediate(inst.getImmediate());
        entry.setStartedExecution(false);
        entry.setCacheLatencyApplied(false);
        entry.setInstructionIndex(pcIndex);
        entry.setRawText(inst.getRawText());
        assignOperands(entry, inst);
        entry.setRemainingCycles(latencyFor(inst.getOpcode()));
        if (pcIndex < instStages.size()) instStages.set(pcIndex, InstructionStatus.Stage.ISSUED);
        pcIndex++;
    }

    private int latencyFor(Opcode opcode) {
        switch (opcode) {
            case ADD, SUB, ADD_D, SUB_D, ADDI, SUBI, DADDI, DSUBI -> { return config.getLatencyConfig().getAddLatency(); }
            case MUL, MUL_D -> { return config.getLatencyConfig().getMulLatency(); }
            case DIV, DIV_D -> { return config.getLatencyConfig().getDivLatency(); }
            case LW, LD, L_S, L_D -> { return config.getLatencyConfig().getLoadLatency(); }
            case SW, SD, S_S, S_D -> { return config.getLatencyConfig().getStoreLatency(); }
            case BEQ, BNE -> { return config.getLatencyConfig().getBranchLatency(); }
        }
        return 1;
    }

    private ReservationStationEntry mapInstructionToStation(Instruction inst) {
        ReservationStationType type;
        if (inst.getOpcode().isFPAddGroup()) type = ReservationStationType.FP_ADD;
        else if (inst.getOpcode().isFPMulGroup()) type = ReservationStationType.FP_MUL;
        else if (inst.getOpcode() == com.example.tomasulo.model.Opcode.ADDI || inst.getOpcode() == com.example.tomasulo.model.Opcode.DADDI) type = ReservationStationType.INT_ADD;
        else if (inst.getOpcode() == com.example.tomasulo.model.Opcode.SUBI || inst.getOpcode() == com.example.tomasulo.model.Opcode.DSUBI) type = ReservationStationType.INT_SUB;
        else if (inst.getOpcode().isInteger()) type = ReservationStationType.INT_ALU;
        else if (inst.getOpcode().isLoad()) type = ReservationStationType.LOAD;
        else if (inst.getOpcode().isStore()) type = ReservationStationType.STORE;
        else if (inst.getOpcode().isBranch()) type = ReservationStationType.BRANCH;
        else return null;
        return stations.findFree(type);
    }

    private void assignOperands(ReservationStationEntry entry, Instruction inst) {
        // determine register file (int vs float) based on naming prefix R vs F
        if (inst.getOpcode().isLoad() || inst.getOpcode().isStore()) {
            // effective address = base register value + offset (defer if base pending)
            String base = inst.getOpcode().isStore() ? inst.getSrc2() : inst.getSrc1();
            Integer offset = inst.getOffset();
            if (base != null) {
                RegisterFile rf = base.startsWith("F") ? floatRegisters : intRegisters;
                if (rf.isPending(base)) {
                    entry.setQj(rf.getTag(base));
                } else {
                    entry.setVj(String.valueOf(rf.getValue(base)));
                }
            }
            entry.setAddress(offset); // final address computed later when base ready
            if (inst.getOpcode().isStore()) {
                // store value source is inst.getSrc1() per parser (value to store)
                String valReg = inst.getSrc1();
                if (valReg != null) {
                    RegisterFile rfVal = valReg.startsWith("F") ? floatRegisters : intRegisters;
                    if (rfVal.isPending(valReg)) {
                        entry.setQk(rfVal.getTag(valReg));
                    } else {
                        entry.setVk(String.valueOf(rfVal.getValue(valReg)));
                    }
                }
            } else {
                // load destination tagging
                String dest = inst.getDest();
                if (dest != null) {
                    RegisterFile rfDest = dest.startsWith("F") ? floatRegisters : intRegisters;
                    rfDest.setTag(dest, entry.getName());
                }
            }
            detectHazards(entry, inst);
            return;
        }
        // Arithmetic / branch sources
        String s1 = inst.getSrc1();
        String s2 = inst.getSrc2();
        if (s1 != null) {
            RegisterFile rf1 = s1.startsWith("F") ? floatRegisters : intRegisters;
            if (rf1.isPending(s1)) entry.setQj(rf1.getTag(s1)); else entry.setVj(String.valueOf(rf1.getValue(s1)));
        }
        if (s2 != null && !inst.getOpcode().isInteger()) {
            RegisterFile rf2 = s2.startsWith("F") ? floatRegisters : intRegisters;
            if (rf2.isPending(s2)) entry.setQk(rf2.getTag(s2)); else entry.setVk(String.valueOf(rf2.getValue(s2)));
        }
        // immediate for ADDI/SUBI used as Vk
        if (inst.getOpcode().isInteger() && inst.getImmediate() != null) {
            entry.setVk(String.valueOf(inst.getImmediate()));
        }
        String dest = inst.getDest();
        if (dest != null && !inst.getOpcode().isBranch() && !inst.getOpcode().isStore()) {
            RegisterFile rfDest = dest.startsWith("F") ? floatRegisters : intRegisters;
            // WAW hazard check
            if (rfDest.isPending(dest)) {
                hazardLog.add(new HazardRecord(HazardType.WAW, rfDest.getTag(dest), entry.getName(), dest, cycle));
            }
            rfDest.setTag(dest, entry.getName());
        }
        detectHazards(entry, inst);
    }

    private void detectHazards(ReservationStationEntry entry, Instruction inst) {
        // RAW hazards: any source waiting on a tag
        if (entry.getQj() != null) hazardLog.add(new HazardRecord(HazardType.RAW, entry.getQj(), entry.getName(), inst.getSrc1(), cycle));
        if (entry.getQk() != null) hazardLog.add(new HazardRecord(HazardType.RAW, entry.getQk(), entry.getName(), inst.getSrc2(), cycle));
        // WAR hazard (simplified heuristic): if station writes dest that is currently a source of another busy station without tag
        String dest = inst.getDest();
        if (dest != null) {
            for (ReservationStationEntry e : stations.busyEntries()) {
                if (e == entry) continue;
                if (dest.equals(e.getSrc1Register()) || dest.equals(e.getSrc2Register())) {
                    hazardLog.add(new HazardRecord(HazardType.WAR, entry.getName(), e.getName(), dest, cycle));
                }
            }
        }
        // Address clash hazard: two memory ops to the same address (if known)
        if ((inst.getOpcode().isLoad() || inst.getOpcode().isStore()) && entry.getAddress() != null) {
            for (ReservationStationEntry e : stations.busyEntries()) {
                if (e == entry) continue;
                if ((e.getOpcode() != null && (e.getOpcode().isLoad() || e.getOpcode().isStore())) && e.getAddress() != null) {
                    if (entry.getAddress().equals(e.getAddress())) {
                        hazardLog.add(new HazardRecord(HazardType.ADDRESS_CLASH, entry.getName(), e.getName(), String.valueOf(entry.getAddress()), cycle));
                    }
                }
            }
        }
    }

    private void executePhase() {
        for (ReservationStationEntry e : stations.busyEntries()) {
            Opcode op = e.getOpcode();
            if (op == null) continue;

            // Check operands readiness
            boolean srcJReady = (e.getQj() == null);
            boolean srcKReady = (e.getQk() == null);

            // For memory ops: base in Vj (or Qj) and store value in Vk (or Qk)
            if (op.isLoad()) {
                if (!srcJReady) continue; // wait for base
            } else if (op.isStore()) {
                if (!srcJReady || !srcKReady) continue; // wait for base and value
            } else if (!op.isBranch()) {
                // arithmetic FP/int: need both ready; for ADDI/SUBI Vk may be immediate
                if (!srcJReady || (e.getVk() == null && !srcKReady)) continue;
            } else {
                // Branch needs both operands ready
                if (!srcJReady || !srcKReady) continue;
            }

            // Apply cache latency on first execution step for memory ops
            if ((op.isLoad() || op.isStore()) && !e.isCacheLatencyApplied()) {
                int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                e.setEffectiveAddress(addr);
                boolean hit = dataCache.isHit(addr);
                int extra = config.getCacheConfig().getHitLatency() + (hit ? 0 : config.getCacheConfig().getMissPenalty());
                e.setRemainingCycles(e.getRemainingCycles() + Math.max(0, extra));
                e.setCacheLatencyApplied(true);
            }

            // Address clash handling: serialize loads behind earlier stores to same address
            if (op.isLoad() || op.isStore()) {
                Integer myAddr = e.getEffectiveAddress();
                if (myAddr != null) {
                    for (ReservationStationEntry other : stations.busyEntries()) {
                        if (other == e) continue;
                        if (!other.getOpcode().isStore()) continue;
                        if (other.getEffectiveAddress() == null) continue; // store base not ready yet
                        if (other.getEffectiveAddress().intValue() == myAddr.intValue()) {
                            // If other store is earlier or its value not ready, stall current
                            boolean earlier = other.getInstructionIndex() != null && e.getInstructionIndex() != null && other.getInstructionIndex() < e.getInstructionIndex();
                            boolean storeValuePending = other.getQk() != null;
                            if (earlier || storeValuePending) {
                                hazardLog.add(new HazardRecord(HazardType.ADDRESS_CLASH, other.getName(), e.getName(), null, cycle));
                                continue; // to next station; do not decrement cycles this round
                            }
                        }
                    }
                }
            }

            if (e.getRemainingCycles() > 0) {
                e.setRemainingCycles(e.getRemainingCycles() - 1);
                e.setStartedExecution(true);
                if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                    if (instStages.get(e.getInstructionIndex()) == InstructionStatus.Stage.ISSUED) {
                        instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.EXECUTING);
                    }
                }
                if (e.getRemainingCycles() == 0) {
                    if (op.isStore()) {
                        // perform store, free station; no CDB publish
                        computeResult(e); // side-effect store
                        e.setBusy(false);
                        e.setResultReady(false);
                        e.setResultValue(null);
                        if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                            instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.COMMITTED);
                        }
                        continue;
                    }
                    int result = computeResult(e);
                    e.setResultValue(result);
                    e.setResultReady(true);
                    if (op.isBranch()) {
                        // Resolve branch: adjust PC and free station (no CDB)
                        if (result == 1) {
                            int offset = (e.getImmediate() == null) ? 0 : e.getImmediate();
                            pcIndex = pcIndex + offset;
                            if (pcIndex < 0) pcIndex = 0;
                            if (pcIndex > program.size()) pcIndex = program.size();
                        }
                        e.setBusy(false);
                        e.setResultReady(false);
                        e.setResultValue(null);
                        if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                            instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.COMMITTED);
                        }
                    } else {
                        cdb.requestPublish(e.getName(), e.getResultValue());
                    }
                }
            }
        }
    }

    private int computeResult(ReservationStationEntry e) {
        Opcode op = e.getOpcode();
        // Resolve any pending operands (if tags cleared earlier). For now assume Vj/Vk hold integer strings.
        int vj = 0; int vk = 0;
        if (e.getVj() != null) vj = Integer.parseInt(e.getVj());
        if (e.getVk() != null) vk = Integer.parseInt(e.getVk());
        if (op == null) return 0;
        switch (op) {
            case ADD, ADD_D, DADDI -> { return vj + vk; }
            case ADDI -> { return vj + (e.getImmediate() != null ? e.getImmediate() : vk); }
            case SUB, SUB_D, DSUBI -> { return vj - vk; }
            case SUBI -> { return vj - (e.getImmediate() != null ? e.getImmediate() : vk); }
            case MUL, MUL_D -> { return vj * vk; }
            case DIV, DIV_D -> { return vk == 0 ? 0 : vj / vk; }
            case LW, LD, L_S, L_D -> {
                // Compute effective address
                int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                byte[] word = dataCache.loadWord(addr, memory);
                int b0 = Byte.toUnsignedInt(word[0]);
                int b1 = Byte.toUnsignedInt(word[1]);
                int b2 = Byte.toUnsignedInt(word[2]);
                int b3 = Byte.toUnsignedInt(word[3]);
                return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            }
            case SW, SD, S_S, S_D -> {
                int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                int value = (e.getVk() != null) ? Integer.parseInt(e.getVk()) : 0;
                byte[] data = new byte[4];
                data[0] = (byte)(value & 0xFF);
                data[1] = (byte)((value >> 8) & 0xFF);
                data[2] = (byte)((value >> 16) & 0xFF);
                data[3] = (byte)((value >> 24) & 0xFF);
                dataCache.storeWord(addr, data, memory);
                return 0; // stores do not publish meaningful value
            }
            case BEQ -> { return (vj == vk) ? 1 : 0; }
            case BNE -> { return (vj != vk) ? 1 : 0; }
        }
        return 0;
    }

    private void writeBackPhase() {
        CommonDataBus.CdbResult res = cdb.arbitrate();
        if (res == null) return;
        // Free producing station and broadcast to dependents
        for (ReservationStationEntry e : stations.busyEntries()) {
            if (e.getName().equals(res.tag)) {
                e.setBusy(false);
                e.setResultReady(false);
                e.setResultValue(null);
                if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                    instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.WRITTEN);
                }
            }
        }
        // Update waiting stations' operands
        for (ReservationStationEntry e : stations.busyEntries()) {
            if (res.tag.equals(e.getQj())) {
                e.setQj(null);
                e.setVj(String.valueOf(res.value));
            }
            if (res.tag.equals(e.getQk())) {
                e.setQk(null);
                e.setVk(String.valueOf(res.value));
            }
        }
        // Update register files whose tag matches
        publishToRegisterFile(intRegisters, res);
        publishToRegisterFile(floatRegisters, res);
    }

    private void publishToRegisterFile(RegisterFile rf, CommonDataBus.CdbResult res) {
        // iterate snapshot tags
        for (String reg : rf.snapshotTags().keySet()) {
            String tag = rf.getTag(reg);
            if (res.tag.equals(tag)) {
                rf.setValue(reg, res.value);
            }
        }
    }

    public CycleSnapshot snapshot() {
        List<StationState> stationStates = stations.all().stream().map(e -> new StationState(
                e.getName(), e.getType(), e.isBusy(), e.getOpcode(), e.getVj(), e.getVk(), e.getQj(), e.getQk(),
                e.getAddress(), e.getRemainingCycles(), e.isResultReady(), e.getResultValue(), e.getDestRegister()
        )).collect(Collectors.toList());
        // Only include hazards for current or previous cycles (could filter if needed)
        List<HazardRecord> recentHazards = hazardLog.stream().filter(h -> h.getCycle() <= cycle).collect(Collectors.toList());
        List<InstructionStatus> queue = new ArrayList<>();
        for (int i = 0; i < program.size(); i++) {
            InstructionStatus.Stage st = (i < instStages.size()) ? instStages.get(i) : InstructionStatus.Stage.QUEUED;
            queue.add(new InstructionStatus(i, program.get(i).getRawText(), st));
        }
        return new CycleSnapshot(cycle, pcIndex, stationStates, intRegisters.snapshotValues(), intRegisters.snapshotTags(),
            floatRegisters.snapshotValues(), floatRegisters.snapshotTags(), recentHazards, queue);
    }

    public ReservationStations getStations() { return stations; }
    public RegisterFile getIntRegisters() { return intRegisters; }
    public RegisterFile getFloatRegisters() { return floatRegisters; }
    public List<HazardRecord> getHazardLog() { return hazardLog; }
}
