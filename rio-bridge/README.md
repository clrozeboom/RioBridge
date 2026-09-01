# rio-bridge

The roboRIO-side WPILib project: reads four analog absolute encoders and a navX2 under 2026
WPILib and republishes them as CAN frames, per the protocol in the repo root
[README.md](../README.md). See that file and [docs/adr/](../docs/adr/) for the design; this file
is build/run mechanics and what's verified.

## Layout

- `src/main/java/frc/robot/protocol/` -- `CanIds`/`CanFrames`: arbitration IDs and the wire
  format (pack/unpack, little-endian). No WPILib dependency, so it's unit tested directly. A
  byte-for-byte copy lives at `../core-integration/src/main/java/frc/robot/protocol/` for the
  Core side -- keep both in sync if you change either.
- `src/main/java/frc/robot/Robot.java` -- the `TimedRobot`: reads sensors and sends every frame
  explicitly each loop (ADR-0004), never `writePacketRepeating`.
- `src/main/java/frc/robot/AttitudeSource.java` / `NavxAttitudeSource.java` -- the navX is behind
  a one-method-per-field interface so `Robot` is testable without the vendor jar. See "What's not
  verified" below.

## Building

Standard WPILib project -- open with the WPILib VS Code extension, or:

```
./gradlew test    # unit + HAL-sim tests
./gradlew build    # full build, requires the roboRIO cross toolchain
./gradlew deploy   # deploy to a roboRIO, requires vendordeps/ set up (see below)
```

**Before your first build:** set your real team number in `.wpilib/wpilib_preferences.json`
(currently a `9999` placeholder -- and that file itself isn't tracked by this repo's
`.gitignore`, so this step doesn't persist across a fresh clone) and add the navX vendordep, see
`vendordeps/README.md`.

## What's verified and what isn't

Every file here except `NavxAttitudeSource.java` was compiled and unit tested against the real
**2026.2.2 WPILib jars** (via GradleRIO 2026.2.1) while writing this, including a HAL-simulation
test that constructs `Robot` and calls `robotPeriodic()` repeatedly. `./gradlew test` passes: 11
tests, `CanFramesTest` (wire format) and `RobotTest` (sensor/CAN wiring under HAL sim).

`NavxAttitudeSource.java` -- the only file touching `com.studica.frc.AHRS` -- could not be
build-verified: its vendordep JSON lives on Studica's own Maven host, and this repo doesn't have
a confirmed URL for the 2026 release (see `vendordeps/README.md`). Everything in it follows the
navX API that's been stable across every navX generation (`getYaw()`/`getPitch()`/`getRoll()`/
`getRate()`/`isConnected()`), but the exact `NavXComType` enum constant for "navX2 on the MXP SPI
port" is a documented assumption, not a confirmed one -- see the TODO comment at the top of that
file.
