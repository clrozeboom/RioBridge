# core-example-rev

A minimal REVLib + WPILib 2027 alpha-7 + `commandsv2-java` smoke test, built to answer one
question: with CTRE Phoenix6 still pinned to WPILib alpha-6 in
[`clrozeboom/BobCat-SystemCore-Clone`](https://github.com/clrozeboom/BobCat-SystemCore-Clone)'s
own `project_examples/ctre/`, can a REV-based example do better against alpha-7? Modeled directly
on that repo's `ctre-commands-v2` example -- same shape, same `org.wpilib.GradleRIO`/`command2`
toolchain, REVLib's `SparkMax` in place of Phoenix6's `TalonFX`.

Originally built against `command3` (BobCat's `ctre-commands-v3`); switched to `command2` since
the RioBridge protocol doesn't need the coroutine-based v3 framework specifically, and v2 is the
more conventional, longer-established one. See "Why v2 instead of v3" below for what changed.

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
- `commandsv2-java` (the classic, `CommandScheduler`-based command framework these v2 example
  projects use) is already published at alpha-7 and its API used here is unchanged from whatever
  version the CTRE example was written against.
- `./gradlew build` succeeds in full -- `compileJava`, `jar`, `startScripts`, `distTar`/`distZip`,
  `assemble`.
- `libREVLibDriver.so` (REVLib's desktop native library) is found and located correctly by
  WPILib's runtime loader.

**What doesn't work**: that `.so` fails to actually load, because it has an unresolved dependency
on `libBackendDriver.so` (confirmed with `readelf -d`), and nothing by that name -- or any
plausible variant -- is published anywhere on `maven.revrobotics.com`. This reads as a packaging
gap in that specific REVLib release, not something wrong with the vendordep here or the code that
uses it -- and it's unrelated to the command framework: it reproduced identically after switching
from `command3` to `command2`, since it's REVLib's native driver failing to load regardless of
which command framework calls it. See `RobotContainerTest`'s javadoc for the exact search that
came up empty, and `vendordeps/REVLib.json` for the coordinates.

**Confirmed independent of WPILib version, too.** Built the same smoke test a third time against
real, version-matched WPILib `2027.0.0-alpha-6` + REVLib `2027.0.0-alpha-6` (not alpha-7) -- see
"Using WPILib alpha-6 instead" below for how that's even possible, since alpha-6 is gone from
frcmaven's normal `release` repo. Identical failure, byte-for-byte same missing
`libBackendDriver.so`. That rules out a WPILib-alpha-7-removed-something-REVLib-needed
explanation: this is REV's packaging gap on its own, regardless of which WPILib version it's
paired with. A weekly Routine checks `maven.revrobotics.com` for a REVLib-driver release that
fixes this and will re-enable `RobotContainerTest` and push automatically if it finds one.

## Using WPILib alpha-6 instead

**You can, but not for anything that needs more than one CAN bus -- which includes RioBridge.**
Two separate things had to be true for the alpha-6 test above to even build:

1. **A repo that still serves it.** frcmaven's normal `release` repository only lists alpha-7 in
   its metadata now (2027 alphas get overwritten there). But `org.wpilib.GradleRIO`, when pinned
   to the exact plugin version `2027.0.0-alpha-6`, resolves its own dependencies against a
   *different*, year-frozen repository -- `https://frcmaven.wpi.edu/artifactory/release-2027` --
   which still has alpha-4 through alpha-6 in full (confirmed: `wpilibj-java`, `wpimath-java`,
   `hal-java`, `wpiutil-java`, `commandsv2-java` all resolve from there with no extra
   configuration beyond pinning the plugin version itself). If you need alpha-6, pin the
   `org.wpilib.GradleRIO` plugin to `2027.0.0-alpha-6` and let it resolve normally -- you don't
   need to add `release-2027` by hand, and you don't need `frcmaven`'s `development` channel
   (which also has near-alpha-6 CI builds, e.g. `2027.0.0-alpha-6-359-g9582ba27b`, but
   `release-2027` is simpler and is what's actually tagged).
2. **A vendordep year tag that matches.** GradleRIO validates each vendordep's declared
   `wpilibYear` against the plugin version, and the two don't number the same way: plugin
   `2027.0.0-alpha-6` expects `"wpilibYear": "2027_alpha5"` (one step behind the numeric alpha --
   confirmed by the error message when it doesn't match, and consistent with what
   `BobCat-SystemCore-Clone`'s own alpha-6-pinned example projects already use).

**Why this doesn't help RioBridge specifically:** alpha-6's `org.wpilib.hardware.bus.CAN` class
has no bus-selecting parameter at all (`CAN(int, int)` / `CAN(int, int, int, int)` -- just a
device ID and manufacturer/type, decompiled and confirmed directly) and `CANPort` -- the
`CAN_S0`/`CAN_S1`/... enum this repo's Core-side integration and CTRE's `CANBus.systemcore(1)`
both depend on -- doesn't exist in the alpha-6 jars at all. **Multi-bus CAN addressing was
introduced between alpha-6 and alpha-7.** Since ADR-0002 (the RioBridge's whole reason for
getting its own dedicated bus) depends on exactly that capability, alpha-6 isn't a viable target
for `core-integration/` regardless of the REVLib question -- alpha-7 (or later) is a hard
requirement, not just what happened to be available. `HAL.initialize()` also regains its
2026-style `(int timeoutMs, int mode)` parameters at alpha-6, for what it's worth, if you're
porting other code back too.

## Why v2 instead of v3

`command3` is a newer, coroutine-based framework (`this.run(coro -> ...)`, `Mechanism` instead of
`SubsystemBase`) that isn't required for anything RioBridge-side -- `command2` is the longer
established, more conventional one (the same `CommandScheduler`/`SubsystemBase`/`Commands.run`
shape most WPILib teams already know), and it turned out simpler to port here too: `SubsystemBase`
stayed a class between alpha-6 and alpha-7 (unlike `command3`'s `Mechanism`, which changed from a
class to an interface -- see the table below, now v2-specific), so the port needed no source
workaround for the command framework itself, only the same GradleRIO/deploy and
`SendableChooser`/`SmartDashboard` changes that affect either framework equally.

## Real API differences found between alpha-6 and alpha-7

Discovered by porting the CTRE example rather than writing from scratch -- each of these broke a
line copied verbatim from `ctre-commands-v2`/`ctre-commands-v3`, confirmed by decompiling the real
alpha-7 classes, not guessed. Rows marked v3-only were found while this was still on `command3`
and don't apply to the current `command2`-based code, but will bite you if you port a `command3`
project instead:

| alpha-6 (what the CTRE examples use) | alpha-7 | Applies to |
|---|---|---|
| `wpi.java.debugJni = false` | `wpi.java.runSimWithDebugJni = false` (renamed) | both |
| `deployArtifact.jarTask = shadowJar` + `wpi.java.configureExecutableTasks(shadowJar)` | Both gone. Apply the `application` plugin, set `application.mainClass`, then `wpi.java.configureApplication(application)` and `deployArtifact.configureApplication(application)` | both |
| `RobotBase.startRobot(Robot.class)` | Only the `Supplier<T>` overload exists now -- `RobotBase.startRobot(Robot::new)` | both |
| `org.wpilib.smartdashboard.SendableChooser` / `SmartDashboard.putData` | **Removed outright** -- the `smartdashboard` package at alpha-7 only has `Field2d`/`Mechanism2d` and friends. Wherever autonomous-chooser-on-the-dashboard moved to, it's not there; this example just hardcodes its one auto routine instead of chasing it down (out of scope here) | both |
| `abstract class Mechanism` (`extends Mechanism`) | `interface Mechanism` (`implements Mechanism`) | v3 only -- `command2`'s `SubsystemBase` stayed a class |

If you're updating `BobCat-SystemCore-Clone`'s own examples to alpha-7, expect to hit at least the
first four regardless of which command framework they're on.

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

- `CommandsV2.json` pins `org.wpilib.commandsv2:commandsv2-java:2027.0.0-alpha-7` explicitly (the
  original in `BobCat-SystemCore-Clone` uses a `"version": "wpilib"` sentinel meaning "match
  whatever WPILib version is in use"; pinned explicitly here instead since that sentinel's exact
  resolution behavior wasn't verified).
- `REVLib.json` is hand-authored from confirmed real Maven coordinates on
  `maven.revrobotics.com` (`REVLib-java`, `REVLib-driver`, `REVLib-cpp`, all `2027.0.0-alpha-6`) --
  REV's actual vendordep JSON wasn't reachable from this sandbox to confirm the UUID or exact
  field set matches their official one. If you install REV's real vendordep through the WPILib VS
  Code extension instead, prefer that over this file.
