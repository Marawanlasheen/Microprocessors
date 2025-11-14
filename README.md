# Tomasulo Algorithm JavaFX Simulator

A GUI-based Tomasulo algorithm simulator using JavaFX. Load MIPS-like code, step cycle-by-cycle, and observe reservation stations and register files update live.

## Current Status
- Core engine: issue/execute/write-back phases with operand readiness (Qj/Qk) gating.
- Corrects load/store base/value mapping; stores do not publish on CDB.
- CDB broadcast updates waiting stations and registers; single winner per cycle (FIFO arbitration).
- Branches resolved without speculation (issue blocks behind unresolved branch); PC updated by immediate offset when taken.
- Cache model integrated for timing: adds hit latency vs miss penalty to memory op latency (data cache only).
- JavaFX GUI: program editor, Load, Step, Run 10, Reset; tables for Reservation Stations, Integer and Float register files; cycle counter.
- Instruction parser for required opcodes and simple formats.

## Still Missing (Planned)
- Instruction queue view and per-instruction status (issue/start/complete).
- Address clash detection for concurrent memory ops and explicit hazard explanations.
- Full cache capacity/eviction visualization and statistics; instruction cache ignored by design.
- User dialogs to edit latencies, station sizes, cache parameters, and initial register values.
- Hazard log table (RAW/WAR/WAW) with cycle timestamps.

## Cache Addressing Strategy
- Byte-addressable memory; word = 4 bytes.
- Direct-mapped data cache: blockAddress = address - (address % blockSize).
- On first execution step of a memory op, extra latency added: `hitLatency` for hits, `hitLatency + missPenalty` for misses.
- Loads copy 4 bytes from block at offset; stores write-through to memory and update resident block.

## Example Program (paste in the GUI)
```
ADDI R1, R0, 100    # R1 = 100
LW R2, 0(R1)        # load word from mem[100]
ADDI R3, R2, 4      # R3 = R2 + 4
BEQ R3, R2, -2      # tiny loop if equal (not taken once R3!=R2)
SW R3, 4(R1)        # store to mem[104]
```

## Building & Running
```
mvn clean javafx:run
```

## Notes
- Floating point ops are treated as integer arithmetic per the project spec.
- Branch immediate is interpreted as PC-relative offset in instruction count units.
- Only data cache misses are modeled; instruction cache is not modeled.
