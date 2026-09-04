# core-example-rev

A minimal REVLib + WPILib 2027 alpha-7 + `commandsv3-java` smoke test, built to answer one
question: with CTRE Phoenix6 still pinned to WPILib alpha-6 in
[`clrozeboom/BobCat-SystemCore-Clone`](https://github.com/clrozeboom/BobCat-SystemCore-Clone)'s
own `project_examples/ctre/`, can a REV-based example do better against alpha-7? Modeled directly
on that repo's `ctre-commands-v3` example -- same shape, same `org.wpilib.GradleRIO`/`command3`
toolchain, REVLib's `SparkMax` in place of Phoenix6's `TalonFX`.

**Not wired into the RioBridge protocol.** This doesn't use `core-integration/`'s
`RioBridgeCan`/`GyroIORioBridge` -- it's scoped to the one question above, per the "minimal smoke
test" scope this was built to. See `core-integration/README.md` for the actual RioBridge
integration.

## Short answer

**REV is in the same position as CTRE, not ahead of it**: `com.revrobotics.frc:REVLib-java` and
`REVLib-driver` are both still published at `2027.0.0-alpha-6`, same as Phoenix6. Everything
*except the very last step* -- the native driver actually linking at runtime -- works at alpha-7:

- The vendordep resolves and its year check passes.
- `REVLib-java` compiles cleanly against the real alpha-7 WPILib jars.
- `commandsv3-java` (the coroutine-based command framework these example projects use) is
  already published at alpha-7 and its API used here is unchanged from whatever version the CTRE
  example was written against.
- `./gradlew build` succeeds in full -- `compileJava`, `jar`, `startScripts`, `distTar`/`distZip`,
  `assemble`.
- `libREVLibDriver.so` (REVLib's desktop native library) is found and located correctly by
  WPILib's runtime loader.

**What doesn't work**: that `.so` fails to actually load, because it has an unresolved dependency
on `libBackendDriver.so` (confirmed with `readelf -d`), and nothing by that name -- or any
plausible variant -- is published anywhere on `maven.revrobotics.com`. This reads as a packaging
gap in that specific REVLib release, not something wrong with the vendordep here or the code that
uses it. See `RobotContainerTest`'s javadoc for the exact search that came up empty, and
`vendordeps/REVLib.json` for the coordinates. Retry once REV ships a `REVLib-driver` build (any
2027 alpha) whose native library doesn't have this dangling dependency.

## Real API differences found between alpha-6 and alpha-7

Discovered by porting the CTRE example rather than writing from scratch -- each of these broke a
line copied verbatim from `ctre-commands-v3`, confirmed by decompiling the real alpha-7 classes,
not guessed:

| alpha-6 (what the CTRE example uses) | alpha-7 |
|---|---|
| `wpi.java.debugJni = false` | `wpi.java.runSimWithDebugJni = false` (renamed) |
| `deployArtifact.jarTask = shadowJar` + `wpi.java.configureExecutableTasks(shadowJar)` | Both gone. Apply the `application` plugin, set `application.mainClass`, then `wpi.java.configureApplication(application)` and `deployArtifact.configureApplication(application)` |
| `abstract class Mechanism` (`extends Mechanism`) | `interface Mechanism` (`implements Mechanism`) |
| `RobotBase.startRobot(Robot.class)` | Only the `Supplier<T>` overload exists now -- `RobotBase.startRobot(Robot::new)` |
| `org.wpilib.smartdashboard.SendableChooser` / `SmartDashboard.putData` | **Removed outright** -- the `smartdashboard` package at alpha-7 only has `Field2d`/`Mechanism2d` and friends. Wherever autonomous-chooser-on-the-dashboard moved to, it's not there; this example just hardcodes its one auto routine instead of chasing it down (out of scope here) |

If you're updating `BobCat-SystemCore-Clone`'s own examples to alpha-7, expect to hit all five.

## Building

```
./gradlew test     # compiles, resolves every dependency including native artifacts, runs tests
./gradlew build    # the above plus jar/startScripts/dist -- still no SystemCore hardware involved
```

`./gradlew test` passes: 1 test, `RobotContainerTest`, currently `@Disabled` for the exact reason
above (re-enable it once REV fixes the native library). Deploying to a real SystemCore is
unverified here regardless -- there's no such hardware in this sandbox.

**Before your first build:** set your real team number in `.wpilib/wpilib_preferences.json`
(currently a `9999` placeholder, and not tracked by this repo's `.gitignore` -- same as
`rio-bridge/`'s).

## vendordeps

- `CommandsV3.json` pins `org.wpilib:commandsv3-java:2027.0.0-alpha-7` explicitly (the original in
  `BobCat-SystemCore-Clone` uses a `"version": "wpilib"` sentinel meaning "match whatever WPILib
  version is in use"; pinned explicitly here instead since that sentinel's exact resolution
  behavior wasn't verified).
- `REVLib.json` is hand-authored from confirmed real Maven coordinates on
  `maven.revrobotics.com` (`REVLib-java`, `REVLib-driver`, `REVLib-cpp`, all `2027.0.0-alpha-6`) --
  REV's actual vendordep JSON wasn't reachable from this sandbox to confirm the UUID or exact
  field set matches their official one. If you install REV's real vendordep through the WPILib VS
  Code extension instead, prefer that over this file.
