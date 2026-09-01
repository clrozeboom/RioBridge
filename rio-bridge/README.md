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
  a one-method-per-field interface so `Robot` is testable with a fake, independent of the vendor
  jar (`RobotTest`), as well as with the real one (`RobotRealNavxTest`).
- `vendordeps/Studica.json` -- the navX2 vendordep.

## Building

Standard WPILib project -- open with the WPILib VS Code extension, or:

```
./gradlew test    # unit + HAL-sim tests
./gradlew build    # full build, requires the roboRIO cross toolchain
./gradlew deploy   # deploy to a roboRIO
```

**Before your first build:** set your real team number in `.wpilib/wpilib_preferences.json`
(currently a `9999` placeholder -- and that file itself isn't tracked by this repo's
`.gitignore`, so this step doesn't persist across a fresh clone).

## What's verified

Every file here was compiled and unit tested against the real **2026.2.2 WPILib jars** (via
GradleRIO 2026.2.1) and the real **`com.studica.frc:Studica-java:2026.0.0`** navX vendor jar.
`./gradlew test` passes: 12 tests --

- `CanFramesTest` -- the wire format, pack/unpack round trips.
- `RobotTest` -- `Robot.robotPeriodic()` under the desktop HAL sim, with a fake `AttitudeSource`
  so both the navX-connected and navX-disconnected paths run.
- `RobotRealNavxTest` -- the public `Robot()` constructor, i.e. the real `NavxAttitudeSource` and
  a real `AHRS`, also under the desktop HAL sim. It logs a real navX sim connection sequence
  (`Instantiating NavX on roboRIO MXP Port` ... `NavX: Connected.`), confirming
  `NavXComType.kMXP_SPI` really does mean "MXP" to the real driver, not just to this code's own
  assumption about it.

Not verified, because nothing in this sandbox could exercise it: the actual sensor hardware, the
actual CAN bus, and the roboRIO cross-compile/deploy step (`./gradlew test` compiles for desktop;
`./gradlew build`/`deploy` additionally need the athena toolchain, which wasn't fetched here).
