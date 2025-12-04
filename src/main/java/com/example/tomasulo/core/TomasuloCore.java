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
    private final List<InstructionStatus> instructionStatuses = new ArrayList<>();
    private int pcIndex = 0;
    private int cycle = 0;
    private final List<HazardRecord> hazardLog = new ArrayList<>();
    private final java.util.Set<Integer> committedThisCycle = new java.util.HashSet<>();

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
        instructionStatuses.clear();
        for (int i = 0; i < program.size(); i++) {
            instStages.add(InstructionStatus.Stage.QUEUED);
            instructionStatuses.add(new InstructionStatus(i, instructions.get(i).getRawText(), InstructionStatus.Stage.QUEUED));
        }
    }

    public int getCycle() { return cycle; }

    public void stepCycle() {
        cycle++;
        System.out.println("\n[DEBUG] ===== Cycle " + cycle + " =====");
        // Clear committedThisCycle from previous cycle
        committedThisCycle.clear();
        // Clear freedThisCycle flags from previous cycle
        for (ReservationStationEntry e : stations.all()) {
            e.setFreedThisCycle(false);
        }
        writeBackPhase();
        executePhase();
        issuePhase();
        // Print status of all reservation stations
        for (ReservationStationEntry e : stations.all()) {
            System.out.println("[DEBUG] Station " + e.getName() + " (" + e.getType() + ") busy=" + e.isBusy() + " opcode=" + (e.getOpcode() == null ? "" : e.getOpcode().name()) + " rem=" + e.getRemainingCycles());
        }
        // Print register values
        System.out.println("[DEBUG] Integer Registers: " + intRegisters.snapshotValues());
        System.out.println("[DEBUG] Float Registers:   " + floatRegisters.snapshotValues());
        // Print memory summary (first 16 words)
        System.out.print("[DEBUG] Memory[0..63]: ");
        for (int i = 0; i < 64; i += 4) {
            int val = memory.loadWordRaw(i);
            System.out.print(val + " ");
        }
        System.out.println();
        // Print completed instructions
        int committed = 0;
        for (int i = 0; i < instStages.size(); i++) {
            if (instStages.get(i) == InstructionStatus.Stage.COMMITTED) committed++;
        }
        System.out.println("[DEBUG] Instructions committed: " + committed + "/" + instStages.size());
        if (committed == instStages.size()) {
            System.out.println("[INFO] All instructions have finished execution and committed.");
        }
    }

    private void issuePhase() {
        if (pcIndex >= program.size()) return;
        // Simple control: do not issue past an unresolved branch
        boolean unresolvedBranch = stations.busyEntries().stream().anyMatch(e -> e.getType() == ReservationStationType.BRANCH);
        if (unresolvedBranch) return;
        Instruction inst = program.get(pcIndex);
        
        // Address clash check: prevent issuing load/store if there's a pending load/store to the same address
        if (inst.getOpcode().isLoad() || inst.getOpcode().isStore()) {
            // Calculate the effective address for the current instruction
            String base = inst.getOpcode().isStore() ? inst.getSrc2() : inst.getSrc1();
            Integer offset = inst.getOffset() != null ? inst.getOffset() : 0;
            
            if (base != null) {
                RegisterFile rf = base.startsWith("F") ? floatRegisters : intRegisters;
                // Only check if base register is ready (not pending)
                if (!rf.isPending(base)) {
                    int baseValue = rf.getValue(base);
                    int effectiveAddress = baseValue + offset;
                    
                    // Check all busy load/store stations AND recently committed ones for address clash
                    // Note: Load after Load to same address is NOT a clash
                    for (ReservationStationEntry e : stations.busyEntries()) {
                        if (e.getOpcode() != null && (e.getOpcode().isLoad() || e.getOpcode().isStore())) {
                            // Check if this is Load-after-Load (allowed, not a clash)
                            boolean currentIsLoad = inst.getOpcode().isLoad();
                            boolean otherIsLoad = e.getOpcode().isLoad();
                            
                            // Check if the earlier instruction has the same address
                            Integer otherAddr = e.getEffectiveAddress();
                            if (otherAddr != null && otherAddr == effectiveAddress) {
                                // Skip if both are loads (no clash)
                                if (currentIsLoad && otherIsLoad) {
                                    continue;
                                }
                                
                                System.out.println("[DEBUG] Cannot issue instruction at PC " + pcIndex + ": " + inst.getRawText() + 
                                    " (address clash with " + e.getName() + " at address " + effectiveAddress + ")");
                                hazardLog.add(new HazardRecord(HazardType.ADDRESS_CLASH, e.getName(), "PC" + pcIndex, "Addr:" + effectiveAddress, cycle));
                                // Mark instruction as stalled
                                if (pcIndex < instStages.size()) {
                                    instStages.set(pcIndex, InstructionStatus.Stage.STALLED);
                                    instructionStatuses.get(pcIndex).setStage(InstructionStatus.Stage.STALLED);
                                }
                                return; // stall due to address clash
                            }
                            // Also check if base is ready but address not computed yet
                            if (otherAddr == null && e.getQj() == null && e.getVj() != null) {
                                int otherBase = Integer.parseInt(e.getVj());
                                int otherOffset = e.getAddress() != null ? e.getAddress() : 0;
                                int otherEffectiveAddr = otherBase + otherOffset;
                                if (otherEffectiveAddr == effectiveAddress) {
                                    // Skip if both are loads (no clash)
                                    if (currentIsLoad && otherIsLoad) {
                                        continue;
                                    }
                                    
                                    System.out.println("[DEBUG] Cannot issue instruction at PC " + pcIndex + ": " + inst.getRawText() + 
                                        " (address clash with " + e.getName() + " at address " + effectiveAddress + ")");
                                    hazardLog.add(new HazardRecord(HazardType.ADDRESS_CLASH, e.getName(), "PC" + pcIndex, "Addr:" + effectiveAddress, cycle));
                                    // Mark instruction as stalled
                                    if (pcIndex < instStages.size()) {
                                        instStages.set(pcIndex, InstructionStatus.Stage.STALLED);
                                        instructionStatuses.get(pcIndex).setStage(InstructionStatus.Stage.STALLED);
                                    }
                                    return; // stall due to address clash
                                }
                            }
                        }
                    }
                    
                    // Also check if any previous load/store to same address has NOT reached COMMITTED yet
                    // OR was committed in this very cycle (must wait until next cycle)
                    // Note: Load after Load to same address is NOT an address clash (loads don't modify memory)
                    for (int i = 0; i < pcIndex; i++) {
                        if (i < program.size() && i < instStages.size()) {
                            Instruction prevInst = program.get(i);
                            if (prevInst.getOpcode().isLoad() || prevInst.getOpcode().isStore()) {
                                // Calculate previous instruction's address
                                String prevBase = prevInst.getOpcode().isStore() ? prevInst.getSrc2() : prevInst.getSrc1();
                                Integer prevOffset = prevInst.getOffset() != null ? prevInst.getOffset() : 0;
                                if (prevBase != null && prevBase.equals(base) && prevOffset.equals(offset)) {
                                    // Check if this is a Load-after-Load case (not an address clash)
                                    boolean currentIsLoad = inst.getOpcode().isLoad();
                                    boolean previousIsLoad = prevInst.getOpcode().isLoad();
                                    if (currentIsLoad && previousIsLoad) {
                                        // Load after Load to same address is allowed - no clash
                                        continue;
                                    }
                                    
                                    // Address clash: Store after Load, Load after Store, or Store after Store
                                    // Same base and offset - check if committed AND not in this cycle
                                    if (instStages.get(i) != InstructionStatus.Stage.COMMITTED || committedThisCycle.contains(i)) {
                                        System.out.println("[DEBUG] Cannot issue instruction at PC " + pcIndex + ": " + inst.getRawText() + 
                                            " (waiting for previous instruction at PC " + i + " to commit in previous cycle)");
                                        hazardLog.add(new HazardRecord(HazardType.ADDRESS_CLASH, "PC" + i, "PC" + pcIndex, "Addr:" + effectiveAddress, cycle));
                                        // Mark instruction as stalled
                                        if (pcIndex < instStages.size()) {
                                            instStages.set(pcIndex, InstructionStatus.Stage.STALLED);
                                            instructionStatuses.get(pcIndex).setStage(InstructionStatus.Stage.STALLED);
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        ReservationStationEntry entry = mapInstructionToStation(inst);
        if (entry == null) {
            System.out.println("[DEBUG] Cannot issue instruction at PC " + pcIndex + ": " + inst.getRawText() + " (no free reservation station of required type)");
            return; // structural stall
        }
        System.out.println("[DEBUG] Issued instruction at PC " + pcIndex + ": " + inst.getRawText());
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
        if (pcIndex < instStages.size()) {
            instStages.set(pcIndex, InstructionStatus.Stage.ISSUED);
            instructionStatuses.get(pcIndex).setStage(InstructionStatus.Stage.ISSUED);
            instructionStatuses.get(pcIndex).setIssueCycle(cycle);
        }
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

            // Prevent starting execution in the same cycle operands become ready
            if (e.isBecameReadyThisCycle()) {
                e.setBecameReadyThisCycle(false);
                continue;
            }

            // Check operands readiness
            boolean srcJReady = (e.getQj() == null);
            boolean srcKReady = (e.getQk() == null);

            // For memory ops: base in Vj (or Qj) and store value in Vk (or Qk)
            if (op.isLoad()) {
                if (!srcJReady) continue; // wait for base
            } else if (op.isStore()) {
                if (!srcJReady || !srcKReady) continue; // wait for base and value
            } else if (!op.isBranch()) {
                // arithmetic FP/int: need both ready
                // For ADDI/SUBI, Vk contains the immediate value (always ready)
                // For other ops, check if Qk is clear (operand ready)
                if (!srcJReady || !srcKReady) continue;
            } else {
                // Branch needs both operands ready
                if (!srcJReady || !srcKReady) continue;
            }

            // Apply cache latency on first execution step for LOAD ops only
            // Stores use the configured store latency instead
            if (op.isLoad() && !e.isCacheLatencyApplied()) {
                int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                e.setEffectiveAddress(addr);
                System.out.println("[EXECUTE] LOAD " + e.getName() + " - Base: " + base + ", Offset: " + e.getAddress() + ", Effective Address: " + addr);
                boolean hit = dataCache.isHit(addr);
                // On cache hit: add hit latency cycles
                // On cache miss: add hit latency + miss penalty cycles, then fetch block into cache
                if (hit) {
                    System.out.println("[EXECUTE] Cache HIT - Adding hit latency: " + config.getCacheConfig().getHitLatency());
                    e.setRemainingCycles(e.getRemainingCycles() + config.getCacheConfig().getHitLatency());
                } else {
                    System.out.println("[EXECUTE] Cache MISS - Adding hit latency + miss penalty: " + (config.getCacheConfig().getHitLatency() + config.getCacheConfig().getMissPenalty()));
                    e.setRemainingCycles(e.getRemainingCycles() + config.getCacheConfig().getHitLatency() + config.getCacheConfig().getMissPenalty());
                }
                // Fetch the data from cache (will load block on miss if not already loaded)
                // Cache this data in the reservation station to avoid fetching again in computeResult
                System.out.println("[EXECUTE] Fetching data from cache for address: " + addr);
                byte[] word = dataCache.loadWord(addr, memory);
                e.setLoadedData(word);
                System.out.println("[EXECUTE] Data cached in reservation station " + e.getName());
                e.setCacheLatencyApplied(true);
            } else if (op.isStore() && !e.isCacheLatencyApplied()) {
                // For stores, just compute effective address (no cache penalty)
                int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                e.setEffectiveAddress(addr);
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
                if (!e.isStartedExecution()) {
                    e.setStartedExecution(true);
                    if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                        if (instStages.get(e.getInstructionIndex()) == InstructionStatus.Stage.ISSUED) {
                            instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.EXECUTING);
                            instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.EXECUTING);
                            instructionStatuses.get(e.getInstructionIndex()).setExecuteStartCycle(cycle);
                        }
                    }
                } else {
                    e.setStartedExecution(true);
                }
            }
            
            // Check if instruction completed (either just now or cache hit made it 0)
            if (e.getRemainingCycles() == 0) {
                // Mark as started if not already (for cache hits that complete immediately)
                if (!e.isStartedExecution()) {
                    e.setStartedExecution(true);
                    if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                        if (instStages.get(e.getInstructionIndex()) == InstructionStatus.Stage.ISSUED) {
                            instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.EXECUTING);
                            instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.EXECUTING);
                            instructionStatuses.get(e.getInstructionIndex()).setExecuteStartCycle(cycle);
                        }
                    }
                }
                
                // Set execute end cycle ONLY if result is not already ready (i.e., just finished execution)
                // This prevents updating executeEndCycle while waiting for CDB
                if (!e.isResultReady() && e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                    instructionStatuses.get(e.getInstructionIndex()).setExecuteEndCycle(cycle);
                }
                
                if (op.isStore()) {
                    // Mark store as ready for write-back (will be performed in next cycle)
                    e.setResultValue(0); // dummy value for stores
                    e.setResultReady(true);
                    // Store will be performed in write-back phase, then station freed
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
                        instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.COMMITTED);
                        instructionStatuses.get(e.getInstructionIndex()).setCommitCycle(cycle);
                        committedThisCycle.add(e.getInstructionIndex()); // Track commit this cycle
                    }
                } else {
                    cdb.requestPublish(e.getName(), e.getResultValue());
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
                // Use the cached data that was loaded during execute phase
                // This avoids redundant cache/memory access
                System.out.println("[COMPUTE] LOAD - Getting cached data from reservation station " + e.getName());
                byte[] word = e.getLoadedData();
                if (word == null) {
                    // Fallback: should not happen if execute phase worked correctly
                    System.out.println("[COMPUTE] WARNING: LoadedData is null! Fetching from cache (this shouldn't happen)");
                    int base = (e.getVj() != null) ? Integer.parseInt(e.getVj()) : 0;
                    int addr = base + (e.getAddress() == null ? 0 : e.getAddress());
                    System.out.println("[COMPUTE] Fallback address: " + addr);
                    word = dataCache.loadWord(addr, memory);
                } else {
                    System.out.println("[COMPUTE] Using cached data - no cache access needed");
                }
                int b0 = Byte.toUnsignedInt(word[0]);
                int b1 = Byte.toUnsignedInt(word[1]);
                int b2 = Byte.toUnsignedInt(word[2]);
                int b3 = Byte.toUnsignedInt(word[3]);
                int result = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
                System.out.println("[COMPUTE] LOAD result: " + result);
                return result;
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
        // First, handle stores that are ready to write back (completed execution)
        for (ReservationStationEntry e : stations.busyEntries()) {
            if (e.getOpcode() != null && e.getOpcode().isStore() && e.isResultReady()) {
                // Perform the actual store operation
                computeResult(e); // side-effect: writes to memory/cache
                System.out.println("[DEBUG] Store instruction " + e.getName() + " completed write-back to memory.");
                
                // Free the reservation station
                e.setBusy(false);
                e.setFreedThisCycle(true);  // Mark as freed this cycle
                e.setResultReady(false);
                e.setResultValue(null);
                
                // Mark as committed
                if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                    instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.COMMITTED);
                    instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.COMMITTED);
                    instructionStatuses.get(e.getInstructionIndex()).setCommitCycle(cycle);
                    committedThisCycle.add(e.getInstructionIndex()); // Track commit this cycle
                }
            }
        }
        
        // Then handle CDB arbitration for non-store instructions
        CommonDataBus.CdbResult res = cdb.arbitrate();
        if (res == null) return;
        System.out.println("[DEBUG] CDB publishing: tag=" + res.tag + ", value=" + res.value);
        // Free producing station and broadcast to dependents
        for (ReservationStationEntry e : stations.busyEntries()) {
            if (e.getName().equals(res.tag)) {
                e.setBusy(false);
                e.setFreedThisCycle(true);  // Mark as freed this cycle
                System.out.println("[DEBUG] Reservation station " + e.getName() + " (type " + e.getType() + ") freed after write-back.");
                e.setResultReady(false);
                e.setResultValue(null);
                if (e.getInstructionIndex() != null && e.getInstructionIndex() < instStages.size()) {
                    // Advance status: WRITTEN -> COMMITTED for non-store/branch
                    Instruction inst = program.get(e.getInstructionIndex());
                    if (inst.getOpcode().isStore() || inst.getOpcode().isBranch()) {
                        instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.WRITTEN);
                        instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.WRITTEN);
                    } else {
                        instStages.set(e.getInstructionIndex(), InstructionStatus.Stage.COMMITTED);
                        instructionStatuses.get(e.getInstructionIndex()).setStage(InstructionStatus.Stage.COMMITTED);
                        instructionStatuses.get(e.getInstructionIndex()).setCommitCycle(cycle);
                        committedThisCycle.add(e.getInstructionIndex()); // Track commit this cycle
                    }
                }
            }
        }
        // Update waiting stations' operands
        for (ReservationStationEntry e : stations.busyEntries()) {
            boolean becameReady = false;
            if (res.tag.equals(e.getQj())) {
                System.out.println("[DEBUG] Station " + e.getName() + " Qj matched tag " + res.tag + ", setting Vj=" + res.value);
                e.setQj(null);
                e.setVj(String.valueOf(res.value));
                becameReady = true;
            }
            if (res.tag.equals(e.getQk())) {
                System.out.println("[DEBUG] Station " + e.getName() + " Qk matched tag " + res.tag + ", setting Vk=" + res.value);
                e.setQk(null);
                e.setVk(String.valueOf(res.value));
                becameReady = true;
            }
            if (becameReady) {
                e.setBecameReadyThisCycle(true);
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
                System.out.println("[DEBUG] Register " + reg + " tag matched CDB tag " + res.tag + ", updating value to " + res.value + " and clearing tag.");
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
        return new CycleSnapshot(cycle, pcIndex, stationStates, intRegisters.snapshotValues(), intRegisters.snapshotTags(),
            floatRegisters.snapshotValues(), floatRegisters.snapshotTags(), recentHazards, new ArrayList<>(instructionStatuses));
    }

    public ReservationStations getStations() { return stations; }
    public RegisterFile getIntRegisters() { return intRegisters; }
    public RegisterFile getFloatRegisters() { return floatRegisters; }
    public List<HazardRecord> getHazardLog() { return hazardLog; }
    public Memory getMemory() { return memory; }
    public DataCache getDataCache() { return dataCache; }
}
