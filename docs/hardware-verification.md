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

**What/why:** `core-integration/`'s own source confirmed the real 2027.0.0-alpha-7 API
contradicts itself -- the field's javadoc says milliseconds, `setStreamData`'s parameter javadoc
on the same class says nanoseconds. `RioBridgeCanDemux` picks milliseconds; this either confirms
that or tells you to fix it.

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
     wall-clock elapsed=1.950s, raw timestamp delta=1950, secondsPerUnit=1e-03
     MILLISECONDS (secondsPerUnit ~= 1e-3): matches the field javadoc.
   ```

**Pass criteria:** the verdict line says `MILLISECONDS` or `NANOSECONDS` outright (not
`MICROSECONDS`, which neither javadoc claimed and deserves a second look, and not
`UNRECOGNIZED`, which means something's wrong with the setup, not the units -- most likely the
RioBridge isn't transmitting, isn't on this bus, or isn't powered; the check prints "FAILED" if
it never saw two Status frames within its timeout).

**If it says NANOSECONDS:** `RioBridgeCanDemux.TIMESTAMP_TO_SECONDS` is currently `1.0 / 1000.0`
(milliseconds). Change it to `1.0 / 1_000_000_000.0`, and drop the "confirmed genuinely
ambiguous" note in `core-integration/README.md` and the root README in favor of the actual
answer.

## 2. HAT channel 1 termination jumper

Purely physical -- there's nothing to run.

**Why it matters:** ADR-0002/the root README's Topology section rely on exactly two 120 Ohm
terminators on the RioBridge bus -- the roboRIO's internal one, and the HAT channel 1's. One
missing or extra terminator degrades signal integrity in a way that often *doesn't* show up as
outright failures at low traffic, just increasing bit errors under load -- which would confound
item 3's results if you check this after, not before.

**Steps:**

1. Power off the roboRIO, the Core, and anything else on this bus.
2. With the CAN cable still connected end-to-end (RioBridge to HAT channel 1), measure DC
   resistance between CAN_H and CAN_L with a multimeter, at either end of the bus -- doesn't
   matter which, since you're measuring the two terminators in parallel through the cable.
3. Compare against expected values:
   - **~60 Ohms** -- both terminators present (roboRIO's internal 120 Ohm + HAT channel 1's 120
     Ohm, in parallel). This is the pass case.
   - **~120 Ohms** -- only one terminator is in the circuit. Since the roboRIO's is internal and
     can't be accidentally removed, this means the HAT channel 1 jumper isn't set (or the HAT
     doesn't terminate that channel the way assumed). Set it and re-measure.
   - **Very high / open** -- a broken connection somewhere in the cable or connector, not a
     termination problem. Check continuity of CAN_H and CAN_L individually.
   - **Near 0 / short** -- CAN_H and CAN_L are shorted together somewhere. Do not power this bus
     on until that's fixed.

## 3. MCP2515 RX headroom at 100 Hz with bus 0 loaded

**What/why:** both HAT channels share the Pi's SPI master. Bus 0 (the drivetrain's SPARK MAXes +
PDH) is far busier than bus 1 (the RioBridge alone), so the open question is whether reading bus
1's stream session ever falls behind while bus 0 is being serviced.

**Tool:** [`DiagnosticsRobot`](../core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/diagnostics/DiagnosticsRobot.java)
-- the same one from item 1; it keeps running after the one-shot timestamp check.

**Steps:**

1. Deploy `DiagnosticsRobot` as in item 1, with the RioBridge powered and transmitting.
2. **Actually load bus 0** -- command the swerve drivetrain (on blocks, or however you'd safely
   bench-test it) for the duration of the run. An idle bus 0 doesn't exercise the shared SPI
   master at all, and would make this check pass trivially without checking anything.
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

## 5. AdvantageKit alpha-4 / WPILib alpha-7 build compatibility

This is an external-repo check -- [BobcatRobotics/SystemCore-Clone](https://github.com/BobcatRobotics/SystemCore-Clone),
not this repo -- so there's no script here for it. Also worth knowing going in: this is now a
version *bump* to verify, not just a build-together check. The root README's stated WPILib target
moved from alpha-6 to alpha-7 (alpha-6 is no longer resolvable from frcmaven -- old 2027 alphas
get overwritten there rather than retained), and `core-integration/` in this repo was updated and
build-verified against alpha-7 accordingly. That update never touched AdvantageKit -- this repo
doesn't depend on it at all -- so whether AdvantageKit alpha-4 (presumably built and tested
against alpha-6) still builds against alpha-7 is exactly the open question here, not a formality.

**Steps:**

1. Clone (or open your existing checkout of) `BobcatRobotics/SystemCore-Clone`.
2. Check what's actually pinned: the WPILib version in its `build.gradle` (the
   `edu.wpi.first.GradleRIO`/equivalent plugin version, or a `wpilibVersion` property) and the
   AdvantageKit version in its `vendordeps/*.json`.
3. Bump the WPILib version to `2027.0.0-alpha-7` if it isn't already, then `./gradlew build` (or
   your project's equivalent).
4. If it fails, the error is almost always a version mismatch between AdvantageKit and WPILib
   (AdvantageKit pins a specific WPILib version range per release) -- check AdvantageKit's release
   notes for which WPILib alpha it expects. If alpha-4 doesn't support alpha-7 yet, look for a
   newer AdvantageKit alpha before assuming anything in the RioBridge-side code is at fault --
   this repo's own code has no AdvantageKit dependency to be wrong about.

If you'd rather this session did this check directly: it would need `BobcatRobotics/SystemCore-Clone`
attached to this session first (it isn't currently in scope), since GitHub access here is
allowlisted per-repository.
