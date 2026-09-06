# core-integration

Drop-in files for the Core's WPILib project (the SystemCore-side robot code -- see the root
[README.md](../README.md)'s "Core-side integration" section for the design these implement).
This directory is **not** a robot project itself; nothing here is a `TimedRobot`, and it can't
deploy to anything. Its `build.gradle` exists only so these files could be compiled and tested
against real WPILib jars while writing them -- see "What's verified" below.

## What's here, and where it goes in your actual Core project

Copy the packages below into your Core's `src/main/java/`, preserving the package paths (they
say `frc.robot...` because that's the WPILib-standard root package; adjust only if your project's
root package differs):

- `frc/robot/protocol/` -- `CanIds`/`CanFrames`. Byte-for-byte identical to
  `../rio-bridge/src/main/java/frc/robot/protocol/`; keep both in sync if you change either.
- `frc/robot/subsystems/drive/riobridge/`:
  - `RioBridgeCan` -- owns the one CAN device handle for all three RioBridge frames, read via the
    per-device `CAN`/`CANReceiveMessage` API (not a buffered stream session -- see its class
    javadoc for why). Construct **one** per robot and share it between `GyroIORioBridge` and
    whatever reads the encoders (see below) -- don't open a second handle. Its
    `malformedFrameCount()` is the direct signal for "to verify" item 3 (RX headroom) -- see
    `docs/hardware-verification.md`.
  - `RioBridgeCanDemux` -- the actual frame decode logic, JNI-free and unit tested.
  - `AttitudeSample` -- one decoded Attitude frame with its Core-side timestamp.
  - `GyroIO` / `GyroIORioBridge` -- if your project already has a `GyroIO` interface (it does, if
    it started from an AdvantageKit swerve template, which is what the root README targets), skip
    this `GyroIO.java` and change `GyroIORioBridge`'s `implements` clause to point at your
    existing one instead of adding a second, near-identical interface.
  - `diagnostics/` -- not part of the integration itself; bench tools for two items on the root
    README's "to verify" list. See "Diagnostics" below.

**Encoders aren't wired to anything here.** The root README's scope is "reading four analog
absolute encoders and navX attitude" -- the gyro side has a clear AdvantageKit convention to
target (`GyroIO`), but which class reads absolute encoders varies by drivetrain template, and the
README explicitly leaves offsets/inversions/re-zeroing on the Core side (so re-zeroing a module
never means reflashing the RioBridge). Wire your module IO's absolute-encoder read to
`RioBridgeCan.latestEncoders().rawCounts()[n]` (n = 0-3, whatever channel mapping your RioBridge
build uses) rather than guessing a `ModuleIO` shape here.

## Wiring it up

```java
// Wherever your drive subsystem is constructed, e.g. RobotContainer:
RioBridgeCan rioBridgeCan = new RioBridgeCan(CANBusMap.CAN_S1);
GyroIO gyroIO = new GyroIORioBridge(rioBridgeCan);
// ... and wire rioBridgeCan.latestEncoders() into your ModuleIOs, per above.
```

`CANBusMap.CAN_S1` matches the root README's topology (RioBridge on HAT channel 1); use whichever
bus your wiring actually uses. It's a raw HAL bus id (`int`), not a `CANPort` -- `CANPort` doesn't
exist at this project's alpha-6 WPILib pin; see `RioBridgeCan`'s class javadoc for why that's fine
(`CANBusMap` has the exact same values as plain `int` constants).

There's no `maxMessagesPerPoll` to size here, unlike an earlier, stream-session-based version of
this class -- `readPacketLatest` has no buffered session to size, just a single cached packet per
API ID (see `RioBridgeCan`'s class javadoc for what that trades away, and why it's confirmed
working on real hardware regardless). Construction order no longer matters for the reason it used
to (there's no session to overflow if left open-but-unpolled), but there's no reason to
reintroduce a field initializer that does slow work first either.

## Diagnostics

`diagnostics/` has bench tools for the two Core-side items on the root README's "to verify" list
that need a live CAN bus: `TimestampUnitsCheck` (item 1) and `BusHealthMonitor` (item 3), both
driven by `DiagnosticsRobot` -- a throwaway robot program, not part of the real integration. Full
procedure, pass criteria, and how to read the output: [docs/hardware-verification.md](../docs/hardware-verification.md).

## What's verified

Everything here compiles and its tests pass against the **real 2027.0.0-alpha-6 org.wpilib jars**
(`org.wpilib.wpilibj`, `org.wpilib.wpimath`, `org.wpilib.hal`, `org.wpilib.wpiutil`) -- the root
README's stated Core hardware, and (unlike an earlier `alpha-7` detour this repo took and then
backed out of, see the root README's "Hardware this was designed against") the same version
AdvantageKit alpha-4 is confirmed to actually build and run against, not just presumed to. `./gradlew
test`: 24 tests -- `CanFramesTest` (wire format, shared with rio-bridge/), `RioBridgeCanDemuxTest`
(frame decode, built from hand-constructed `CANReceiveMessage`s), and `TimestampUnitsCheckTest` /
`BusHealthMonitorTest` (the diagnostics' own classification logic).

**Note for your own project:** the 2027 alpha jars are compiled for Java 25 (class file major
version 69) -- newer than most machines will have as their default JDK today. This project's
`build.gradle`/`settings.gradle` pin a Java 25 toolchain and let Gradle download one via the
Foojay resolver if none is found locally; if your Core project targets an older Java version,
you'll hit the same `class file has wrong version` error these files did until you do the same
(or your project's WPILib version already forces this and you won't need to do anything).

**Not verified against real hardware or the desktop HAL sim in this exact copy of the files:**
`RioBridgeCan.poll()` and its constructor, and everything in `diagnostics/` that calls `CANJNI` or
`CANAPIJNI` directly, still haven't run as the code sitting in this directory -- there's no
simulated CAN bus in this sandbox to query against. But this exact *design* -- not this exact copy
of the files -- has: it was hand-ported to a real alpha-6-pinned robot project
([clrozeboom/NerdSwerveYAGSL2026](https://github.com/clrozeboom/NerdSwerveYAGSL2026)'s
`claude/riobridge-core-integration` branch), deployed to a real SystemCore, and drove a real
swerve robot's gyro and encoders through autonomous and teleop -- see the findings below, all of
which came from that run and are already applied here.

**The whole design moved off the buffered CAN stream session -- the single biggest finding.**
`RioBridgeCan` originally used `CANJNI.openCANStreamSession`/`readCANStreamSession` with
`CANStreamMessage`. On real hardware, that API never marshaled payload bytes back to Java at all
-- `.length` read 0 on essentially every real frame, at a rate matching the protocol's entire
combined send rate (~220/sec), not an occasional glitch. `RioBridgeCan` now reads each frame on
its own API ID via the older, non-streaming, per-device `CAN`/`CANReceiveMessage` API instead --
confirmed on the same real hardware to marshal payload bytes correctly. See `RioBridgeCan`'s class
javadoc and [docs/wpilib-bug-report-can-stream-payload.md](../docs/wpilib-bug-report-can-stream-payload.md)
for the full drafted bug report. The same stream session also hit a separate, genuine JVM segfault
in its overflow-handling path (`CANStreamOverflowException` null-derefs while being constructed);
moving off it sidesteps that risk entirely too, since there's no buffered session left to overflow.
The tradeoff: no true per-sample buffering anymore, only "the single latest packet per API ID" --
see `RioBridgeCan`'s class javadoc for what that costs.

**`CANReceiveMessage.timestamp`'s units: resolved, and now confirmed two ways.** The root README's
"To verify" list used to flag this as ambiguous, in the *stream* API's `CANStreamMessage`, between
the field's own javadoc (milliseconds, `CLOCK_MONOTONIC`) and `setStreamData`'s parameter javadoc
(nanoseconds) -- on the same class. Neither was right: a real `TimestampUnitsCheck` run (which
still uses the stream API deliberately -- see its class javadoc for why that's safe) measured
`secondsPerUnit ~= 1e-6` (wall-clock elapsed=1.946s against a raw timestamp delta of 1,950,175 over
40 Status frames at 20 Hz) -- **microseconds**. `CANReceiveMessage.timestamp`'s own javadoc
independently says "in microseconds (wpi time)," unambiguously, confirming the same answer a
second way. `RioBridgeCanDemux.TIMESTAMP_TO_SECONDS` is `1.0 / 1_000_000.0`; see its class javadoc
and [docs/hardware-verification.md](../docs/hardware-verification.md) item 1.

**`BusHealthMonitor`/`DiagnosticsRobot` needed a resilience fix, found by the same run.**
`CANJNI.getCANStatus` threw a `HalHandleException` querying the drivetrain bus specifically --
`DiagnosticsRobot` never constructs a device or opens a session on that bus itself (it fully
replaces the real robot code for one deploy), so nothing in that process had ever touched it.
Left uncaught, that crashed the whole diagnostic and took the RioBridge bus's real, working data
down with it. `sampleSafely` in `DiagnosticsRobot` now catches this per bus and prints
`<bus>: status unavailable (...)` instead -- see [docs/hardware-verification.md](../docs/hardware-verification.md)
item 3.
