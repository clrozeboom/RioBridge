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
  - `RioBridgeCan` -- owns the one CAN stream session for all three RioBridge frames. Construct
    **one** per robot and share it between `GyroIORioBridge` and whatever reads the encoders (see
    below) -- don't open a second session. Its `overflowCount()` is the direct signal for "to
    verify" item 3 (RX headroom) -- see `docs/hardware-verification.md`.
  - `RioBridgeCanDemux` -- the actual frame demux/decode logic, JNI-free and unit tested.
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
RioBridgeCan rioBridgeCan = new RioBridgeCan(CANPort.CAN_S1, /* maxMessagesPerPoll= */ 32);
GyroIO gyroIO = new GyroIORioBridge(rioBridgeCan);
// ... and wire rioBridgeCan.latestEncoders() into your ModuleIOs, per above.
```

`CANPort.CAN_S1` matches the root README's topology (RioBridge on HAT channel 1); use whichever
port your wiring actually uses. `maxMessagesPerPoll` just needs to comfortably exceed the number
of RioBridge frames arriving between two calls to `updateInputs` -- at the drive loop's usual 50
Hz against a 100 Hz Attitude frame plus a 20 Hz Status frame, that's at most 3 messages per period;
32 is generous headroom, not a tuned value.

## Diagnostics

`diagnostics/` has bench tools for the two Core-side items on the root README's "to verify" list
that need a live CAN bus: `TimestampUnitsCheck` (item 1) and `BusHealthMonitor` (item 3), both
driven by `DiagnosticsRobot` -- a throwaway robot program, not part of the real integration. Full
procedure, pass criteria, and how to read the output: [docs/hardware-verification.md](../docs/hardware-verification.md).

## What's verified

Everything here compiles and its tests pass against the **real 2027.0.0-alpha-7 org.wpilib jars**
(`org.wpilib.wpilibj`, `org.wpilib.wpimath`, `org.wpilib.hal`, `org.wpilib.wpiutil` -- the root
README cites alpha-6; alpha-7 is what's resolvable from frcmaven as of this writing, since old
2027 alphas get overwritten there rather than retained). `./gradlew test`: 21 tests --
`CanFramesTest` (wire format, shared with rio-bridge/), `RioBridgeCanDemuxTest` (frame demux,
built from hand-constructed `CANStreamMessage`s), and `TimestampUnitsCheckTest` /
`BusHealthMonitorTest` (the diagnostics' own classification logic).

**Note for your own project:** the 2027 alpha jars are compiled for Java 25 (class file major
version 69) -- newer than most machines will have as their default JDK today. This project's
`build.gradle`/`settings.gradle` pin a Java 25 toolchain and let Gradle download one via the
Foojay resolver if none is found locally; if your Core project targets an older Java version,
you'll hit the same `class file has wrong version` error these files did until you do the same
(or your project's WPILib version already forces this and you won't need to do anything).

**Not verified against real hardware or the desktop HAL sim:** `RioBridgeCan.poll()` and its
constructor, and everything in `diagnostics/` that calls `CANJNI` directly (`TimestampUnitsCheck`'s
session, `BusHealthMonitor.sample`) -- there's no simulated CAN bus in this sandbox to open a
stream session or query bus status against, so none of it has actually run. Everything they call
is the exact `CANJNI`/`CANStreamMessage`/`CANStatus` API pulled from the real alpha-7 sources
(`org.wpilib.hardware.hal.can`), not a guess, but "compiles against the real signature" and
"behaves correctly against a live bus" are different claims -- which is exactly what running the
diagnostics on your actual hardware settles.

**Confirmed as a genuine open question, not just this repo's uncertainty:** the root README's "To
verify" list flags `CANStreamMessage.timestamp`'s units as ambiguous between the field's own
javadoc (milliseconds, `CLOCK_MONOTONIC`) and `setStreamData`'s parameter javadoc (nanoseconds) --
*on the same class*, in the real alpha-7 source. `RioBridgeCanDemux` follows the field comment
(milliseconds); see its class javadoc and print a raw value against a known interval before
trusting `GyroIORioBridge`'s 100 ms staleness threshold on hardware.
