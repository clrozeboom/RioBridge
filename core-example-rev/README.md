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

**You can, including for RioBridge's multi-bus CAN needs** -- corrected after actually applying
`core-integration/`'s design to a real alpha-6-pinned project
([clrozeboom/NerdSwerveYAGSL2026](https://github.com/clrozeboom/NerdSwerveYAGSL2026)'s
`claude/swerve-2027-advantagekit` branch) rather than a from-scratch probe; see "The corrected
multi-bus story" below for what that changed here. Two separate things had to be true for the
alpha-6 test above to even build:

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

**The corrected multi-bus story, corrected again:** `CANPort` -- the friendly `CAN_S0`/`CAN_S1`/...
enum -- genuinely doesn't exist in `wpilibj-java` at alpha-6; that part holds. But the claim that
used to sit here -- that `org.wpilib.hardware.bus.CAN` itself has no bus-selecting parameter at
alpha-6 -- was wrong, and was based on a `javap` signature listing (which shows parameter *types*,
not names) rather than the real decompiled source. With the actual source in hand:
`CAN(int busId, int deviceId)` and `CAN(int busId, int deviceId, int deviceManufacturer, int
deviceType)` both take the bus as their **first** parameter, passed straight through to
`CANAPIJNI.initializeCAN(busId, ...)`. So the friendly `CAN` class was never missing bus selection
at alpha-6 either -- only the `CANPort` enum wrapper around it was. `CANJNI.openCANStreamSession`
(and the rest of `CANJNI`) separately already takes a raw bus id `int` as its first argument, same
as at alpha-7, and `org.wpilib.hardware.hal.CANBusMap` -- already on the classpath via `hal-java`,
a transitive dependency of `wpilibj-java` -- exposes the exact same `CAN_S0`/`CAN_S1`/.../`CAN_D19`
values `CANPort` would, just as plain `int` constants instead of enum entries. `CANPort` is a
friendlier wrapper added between alpha-6 and alpha-7; the multi-bus *capability* underneath it,
at every layer we've now actually checked, was there all along.

Confirmed by more than decompiling: `RioBridgeCan`, its diagnostics, and a `GyroIO`/absolute
encoder integration built on it were actually applied to a real alpha-6-pinned project (see
above), swapping `CANPort bus` parameters for `int bus` sourced from `CANBusMap`, and
`./gradlew build` passes in full against the real alpha-6 jars -- not a scratch probe. So alpha-6
*is* a viable target for `core-integration/`'s design; this repo stays on alpha-7 anyway since
there's no reason to prefer the older one once REVLib's native-driver gap turns out to be
unrelated to either version (see above), but a project with some other reason to be stuck on
alpha-6 doesn't have to also give up RioBridge's dedicated-bus design over it. `HAL.initialize()`
also regains its 2026-style `(int timeoutMs, int mode)` parameters at alpha-6, for what it's
worth, if you're porting other code back too.

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
| `Rotation2d.kZero` | `Rotation2d.ZERO` (renamed) | both -- found retargeting `core-integration/`'s `GyroIO.java` back to alpha-6 for this repo's alpha-6 release; confirmed by decompiling both jars, not guessed |

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
