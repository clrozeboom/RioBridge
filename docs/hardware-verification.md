# Hardware verification guide

Step-by-step procedures for the root [README.md](../README.md)'s "To verify before running on
hardware" list. Items 1 and 3 have a runnable diagnostic in `core-integration/`; item 4 has one in
`rio-bridge/`. Items 2 and 5 are a physical check and an external-repo build check respectively,
so they're procedures rather than code.

Do these roughly in this order: 2 (termination) first, since a mis-terminated bus can produce
garbage readings that make 1 and 3 look worse than they are; then 4 (encoder mapping) and 1
(timestamp units), which are quick; then 3 (RX headroom), which wants a longer run under load;
then 5 (build compatibility) whenever is convenient, since it doesn't touch hardware at all.

## 1. `CANStreamMessage.timestamp` units

**Resolved.** `core-integration/`'s own source confirmed the real 2027.0.0-alpha-7 API
contradicts itself -- the field's javadoc says milliseconds, `setStreamData`'s parameter javadoc
on the same class says nanoseconds -- and a real run of this exact tool against real hardware
settled it as neither: `secondsPerUnit ~= 1e-6` (wall-clock elapsed=1.946s against a raw
timestamp delta of 1,950,175 over 40 Status frames at 20 Hz) is **microseconds**.
`RioBridgeCanDemux.TIMESTAMP_TO_SECONDS` is `1.0 / 1_000_000.0` now, not the milliseconds guess
it shipped with. The steps below are kept as a runbook for anyone re-verifying this on their own
hardware (a different Core OS/kernel/HAL build could plausibly differ), not because the answer
is still open.

**Tool:** [`TimestampUnitsCheck`](../core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/diagnostics/TimestampUnitsCheck.java),
driven by [`DiagnosticsRobot`](../core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/diagnostics/DiagnosticsRobot.java)
-- same deploy as item 3 below, so do both in one bench session.

**Steps:**

1. On the Core project, copy in `core-integration/src/main/java/frc/robot/protocol/` and
   `core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/` (including its
   `diagnostics/` subpackage), preserving those package paths.
2. Temporarily point your Core's entry point (its `Main`, wherever it calls something like
   `RobotBase.startRobot(Robot::new)`) at `DiagnosticsRobot::new` instead -- or use the included
   [`DiagnosticsMain`](../core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/diagnostics/DiagnosticsMain.java)
   as an alternate entry point if your build supports pointing at a different main class for one
   deploy.
3. Power on the RioBridge (it needs to actually be transmitting -- this reads real Status frames)
   and deploy `DiagnosticsRobot` to the Core.
4. Watch the console (however you normally view `System.out` from the Core -- SSH + your log
   viewer, AdvantageScope's log tab, etc.; this repo doesn't know your Core project's specific
   setup). Within ~2-10 seconds you'll see:

   ```
   === TimestampUnitsCheck: collecting Status frames on CAN_S1 ===
     wall-clock elapsed=1.946s, raw timestamp delta=1950175, secondsPerUnit=9.98074e-07
     MICROSECONDS (secondsPerUnit ~= 1e-6): neither javadoc claimed this -- worth a second look before trusting it.
   ```

   (That's the actual output from the real run that settled this -- yours should land close to
   `secondsPerUnit ~= 1e-6` too.)

**Pass criteria:** the verdict line says `MICROSECONDS` -- that's the confirmed answer now, not
the surprising case the tool's own message text still frames it as. `MILLISECONDS` or
`NANOSECONDS` would mean a different Core build genuinely behaves differently from the one this
was confirmed against, which is worth its own investigation rather than assuming the fix above
still applies as-is. `UNRECOGNIZED` means something's wrong with the setup, not the units -- most
likely the RioBridge isn't transmitting, isn't on this bus, or isn't powered; the check prints
"FAILED" if it never saw two Status frames within its timeout.

**If you get anything other than MICROSECONDS:** `RioBridgeCanDemux.TIMESTAMP_TO_SECONDS` is
currently `1.0 / 1_000_000.0` (microseconds). Change it to match what you actually measured
(`1.0 / 1000.0` for milliseconds, `1.0 / 1_000_000_000.0` for nanoseconds), and update this file
plus the root README and `core-integration/README.md` to say so.

## 2. HAT channel 1 termination jumper

Purely physical -- there's nothing to run.

**Why it matters:** ADR-0002/the root README's Topology section rely on exactly two 120 Ohm
terminators on the RioBridge bus -- the roboRIO's internal one, and the HAT channel 1's. One
missing or extra terminator degrades signal integrity in a way that often *doesn't* show up as
outright failures at low traffic, just increasing bit errors under load -- which would confound
item 3's results if you check this after, not before.

There are two stages here, and they answer different questions -- don't stop at the first one.

**Steps:**

1. Power off the roboRIO, the Core, and anything else on this bus.
2. **Stage A -- isolated sanity check (optional, but useful for isolating a fault).** With the
   two ends *not* connected to each other, measure DC resistance between CAN_H and CAN_L at each
   connector on its own:
   - RioBridge (roboRIO) alone: expect **~120 Ohms**. This is the roboRIO's fixed internal
     terminator, which is always present by design -- reading 120 Ohms here just confirms that,
     it isn't really testing anything variable.
   - Core's CAN_S1 (HAT channel 1) alone: **~120 Ohms** means that channel's termination jumper
     *is* set (its own local terminator is present and correctly valued -- good news). **Open /
     very high** means the jumper isn't set.

   Getting ~120 Ohms at both ends in this stage is a *pass* for Stage A, not the "only one
   terminator" failure described below -- that failure only applies to a joined-bus measurement
   (Stage B). Measured in isolation, 120 Ohms at each end separately is two independent
   confirmations that each side's terminator is present, which is exactly what you want to see
   before moving on.
3. **Stage B -- the actual test.** Connect the CAN cable end-to-end (RioBridge to HAT channel 1),
   then measure once, at either end -- it now matters that they're joined, which is what makes
   "either end" give the same reading (CAN_H and CAN_L become one shared node pair across a
   continuous bus, so resistance measured anywhere on it is identical). Compare against:
   - **~60 Ohms** -- both terminators present *and* in circuit together (120 Ohm parallel 120
     Ohm). This is the pass case for item 2.
   - **~120 Ohms** -- only one terminator is actually in the joined circuit, even if Stage A
     showed both present individually. That points to a continuity problem in the cable or
     connector, not a missing jumper (you already confirmed the jumper's there in Stage A) --
     check continuity (not resistance) between the RioBridge's CAN_H pin and the Core's CAN_H pin
     (expect a beep / near-0 Ohms), then CAN_L to CAN_L. Whichever doesn't show continuity is a
     bad crimp, loose connector, or broken wire.
   - **Very high / open** -- a broken connection somewhere in the cable or connector. Same
     continuity check as above.
   - **Near 0 / short** -- CAN_H and CAN_L are shorted together somewhere. Do not power this bus
     on until that's fixed.

## 3. MCP2515 RX headroom at 100 Hz with bus 0 loaded

**What/why:** both HAT channels share the Pi's SPI master. Bus 0 (the drivetrain's SPARK MAXes +
PDH) is far busier than bus 1 (the RioBridge alone), so the open question is whether reading bus
1's stream session ever falls behind while bus 0 is being serviced.

**Tool:** [`DiagnosticsRobot`](../core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/diagnostics/DiagnosticsRobot.java)
-- the same one from item 1; it keeps running after the one-shot timestamp check.

**A CAN_S0 (or CAN_S1) reading can legitimately be unavailable, not just regressed.**
`CANJNI.getCANStatus` can throw at the HAL layer for a bus nothing in this process has otherwise
touched -- confirmed on real hardware, on the drivetrain bus specifically, since
`DiagnosticsRobot` replaces the real robot code for this one deploy and so never constructs a
SPARK MAX or opens any session on bus 0 itself. `DiagnosticsRobot` now catches this per bus and
prints `<bus>: status unavailable (...)` instead of crashing and losing visibility into the
*other* bus's real data. If you see that for `CAN_S0`, it most likely means that bus's SocketCAN
interface isn't brought up on the Core yet (or nothing on it has ever been powered/queried in
this process) -- not a RioBridge problem, and not something to debug via this tool.

**Steps:**

1. Deploy `DiagnosticsRobot` as in item 1, with the RioBridge powered and transmitting.
2. **Load bus 0 if you can.** `DiagnosticsRobot` fully replaces the real robot program for this
   deploy, so it can't command the swerve drivetrain itself -- there's no code path left that
   would. What bus-0 traffic you do see comes from whatever's independently powered on that bus
   (a PDH broadcasts its own status continuously just from being powered; SPARK MAXes send
   periodic status frames at idle even uncommanded), which is real traffic but lighter than a
   commanded drivetrain would produce. If you need a genuinely loaded bus 0, do this measurement
   with the real robot code running instead (temporarily add this diagnostic's prints into it,
   rather than swapping `Main` to `DiagnosticsRobot`), or accept the idle-bus reading as a lighter
   version of the same check. An idle bus 0 that still shows 0% utilization and no CAN_S0 status
   at all is worth investigating on its own -- it means nothing on that bus is even powered up.
3. Let it run for at least a few minutes -- long enough to see whether problems are transient
   (one bad SPI transaction) or sustained (consistently falling behind).
4. Watch the once-per-second console output:

   ```
   CAN_S0: util=23.4% busOff=0 txFull=0 rxErr=0 txErr=0
   CAN_S1: util=1.2% busOff=0 txFull=0 rxErr=0 txErr=0
     RioBridgeCan: attitudeFramesLastSecond=100 (expect ~100 at 100 Hz) overflowCount=0
   ```

**Pass criteria, all of these for the whole run:**

- `overflowCount` stays at `0`. This is the direct signal: it only increments when
  `RioBridgeCan.poll()` catches a `CANStreamOverflowException`, meaning the session's buffer
  filled between polls and a frame was actually dropped, not just delayed.
- `attitudeFramesLastSecond` stays close to 100 (a few frames off from jitter is normal; a
  sustained drop below is a problem even if `overflowCount` hasn't incremented yet).
- Neither bus shows a `REGRESSED` flag -- i.e. `busOffCount`, `txFullCount`,
  `receiveErrorCount`, and `transmitErrorCount` never increase from one second to the next, on
  either bus.

**If it fails:** the fix is almost certainly on the Core side (reduce how much else shares the
SPI master, or how often you poll), not something to change in the RioBridge -- the RioBridge's
send rates are fixed by the protocol table and ADR-0004's explicit-sends design.

## 4. Encoder channels: onboard vs. MXP

**What/why:** `rio-bridge/`'s `Robot.java` currently assumes all four absolute encoders are on
onboard analog channels 0-3 (there's a `TODO` at that exact line). If any are actually wired to
the MXP breakout instead, that assumption is wrong and the wrong physical encoder ends up in each
CAN frame slot.

**Tool:** [`EncoderChannelDiagnostic`](../rio-bridge/src/main/java/frc/robot/diagnostics/EncoderChannelDiagnostic.java).

**Steps:**

1. In `rio-bridge/src/main/java/frc/robot/Main.java`, temporarily change

   ```java
   RobotBase.startRobot(Robot::new);
   ```

   to

   ```java
   RobotBase.startRobot(frc.robot.diagnostics.EncoderChannelDiagnostic::new);
   ```

2. `./gradlew deploy` (or deploy via the WPILib VS Code extension, as usual).
3. Open the console (Driver Station's "View Console", or riolog in VS Code). You'll see a line
   twice a second:

   ```
   [12] onboard[0]=2048  onboard[1]=13  onboard[2]=4091  onboard[3]=2  MXP[4]=0  MXP[5]=4095  MXP[6]=1  MXP[7]=4088
   ```

4. Rotate each of the four encoders by hand, one at a time, and note which labeled channel's
   value tracks it smoothly. A floating (unwired) analog channel reads noisy near one rail (0 or
   4095), not a stable value that moves with the encoder.

**Pass criteria:** all four encoders show up on `onboard[0]`-`onboard[3]`, and `MXP[4]`-`MXP[7]`
stay flat/noisy throughout.

**If an encoder shows up on an MXP channel instead:** update `ENCODER_CHANNELS` in
`rio-bridge/src/main/java/frc/robot/Robot.java` to the real channel numbers (0-7, matching
`AnalogInput`'s own convention: 0-3 onboard, 4-7 MXP -- confirmed directly against the WPILib
2026.2.2 source while building this diagnostic).

**When done:** revert `Main.java` to `RobotBase.startRobot(Robot::new)` and redeploy the real
`Robot` before leaving the bench.

## 5. Core toolchain / vendor library build compatibility at alpha-7

**Updated after actually cloning the real Core project.** [`clrozeboom/BobCat-SystemCore-Clone`](https://github.com/clrozeboom/BobCat-SystemCore-Clone)
turned out not to contain AdvantageKit or REV SPARK MAX at all -- it's mostly a Raspberry Pi
OS/CAN-HAT setup repo, with two example robot projects under `project_examples/ctre/` (one on
`commandsv2-java`, one on the newer coroutine-based `commandsv3-java`), both built on CTRE
Phoenix6 and both still pinned to WPILib `2027.0.0-alpha-6`. If your actual Core project's stack
differs from this, treat this item's steps as a template and substitute your own vendor library
and framework.

`core-example-rev/` in this repo already did the empirical work for the REV side of this
question: REVLib is in the same spot as CTRE (published at alpha-6, not alpha-7), and porting the
CTRE-shaped example to alpha-7 (tried against both `command2` and `command3`) surfaced real API
differences along the way (renamed/removed GradleRIO properties, `RobotBase.startRobot`'s
`Class<T>` overload being removed, `SendableChooser`/`SmartDashboard` disappearing outright, plus
one `command3`-only change -- `Mechanism` going from a class to an interface) -- see
`core-example-rev/README.md`'s table before you hit the same ones. The one thing it could not get
working, on either command framework, is REVLib's native driver actually loading (a missing
`libBackendDriver.so` dependency, confirmed to be REV's packaging gap, not this repo's) -- if
you're checking Phoenix6 instead, its native driver may or may not have the equivalent problem;
that's what step 3 below actually tests. A weekly Routine watches for a REVLib-driver release
that fixes this.

**Staying on WPILib alpha-6 instead of bumping to alpha-7 is possible, RioBridge included.**
`core-example-rev/README.md`'s "Using WPILib alpha-6 instead" section has the mechanics (a
year-frozen `release-2027` frcmaven repo still serves alpha-4 through alpha-6 in full, and
reproduced the exact same REVLib failure there too -- ruling out an alpha-7-specific cause) and
the corrected multi-bus story: alpha-6's friendly `CAN`/`CANPort` API genuinely has no
bus-selecting option, but `core-integration/`'s design never used that API -- it calls
`CANJNI.openCANStreamSession` directly, which already takes a raw HAL bus id (`int`) at alpha-6,
same as alpha-7, just without `CANPort`'s enum wrapper around it
(`org.wpilib.hardware.hal.CANBusMap` has the same values as plain ints). Confirmed by actually
applying this repo's design to a real alpha-6 project, not just decompiling -- see that section.
This repo still targets alpha-7 since there's no reason to prefer the older one once the REVLib
gap turned out to be version-independent, but alpha-6 was never actually the blocker it was first
reported as.

**Steps, for whatever vendor library and framework your Core project actually uses:**

1. Open your Core project (or `clrozeboom/BobCat-SystemCore-Clone`'s `ctre-commands-v2`/
   `ctre-commands-v3` if you have no other yet).
2. Check what's actually pinned: the `org.wpilib.GradleRIO` plugin version in `build.gradle`, and
   each vendor library's version in `vendordeps/*.json`.
3. Bump the WPILib/GradleRIO version to `2027.0.0-alpha-7` (or whatever is current -- confirm on
   frcmaven, since old 2027 alphas get overwritten there rather than retained) and try
   `./gradlew build`. Expect real compile errors if your code touches anything in
   `core-example-rev/README.md`'s table; fix those first so a subsequent failure is actually about
   vendor library compatibility, not leftover alpha-6 API usage.
4. If a vendor library's own classes fail to resolve or compile, check its release notes for
   which WPILib alpha it expects -- look for a newer vendor release before assuming the RioBridge
   integration code is at fault, since neither `core-integration/` nor `core-example-rev/` has any
   dependency on AdvantageKit, CTRE, or REV to be wrong about (`core-example-rev/`'s REVLib
   dependency is deliberately isolated to that one directory).
5. If it compiles but a device's native driver won't construct at runtime (an
   `ExceptionInInitializerError` wrapping a native-library load failure, same shape as
   `core-example-rev/`'s), check the missing library's exact name with `readelf -d
   path/to/libTheDriver.so | grep NEEDED` and search the vendor's Maven host for an artifact
   providing it before concluding it's your build's fault.
