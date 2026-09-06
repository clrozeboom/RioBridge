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

**Resolved, and now confirmed two independent ways.** `core-integration/`'s own source confirmed
the real WPILib API contradicts itself for the *stream* API's `CANStreamMessage` -- the field's
javadoc says milliseconds, `setStreamData`'s parameter javadoc on the same class says nanoseconds
-- and a real run of this exact tool against real hardware settled it as neither: `secondsPerUnit
~= 1e-6` (wall-clock elapsed=1.946s against a raw timestamp delta of 1,950,175 over 40 Status
frames at 20 Hz) is **microseconds**. `core-integration/` no longer reads its actual frame data
through the stream API (see item 3 below), but this tool still deliberately does -- see its class
javadoc for why that's safe -- so this measurement stays the way to check the unit. The per-device
API's own `CANReceiveMessage.timestamp` independently documents "in microseconds (wpi time)" in
its own javadoc, unambiguously, confirming the same answer a second, unrelated way.
`RioBridgeCanDemux.TIMESTAMP_TO_SECONDS` is `1.0 / 1_000_000.0`. The steps below are kept as a
runbook for anyone re-verifying this on their own hardware (a different Core OS/kernel/HAL build
could plausibly differ), not because the answer is still open.

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
1 ever falls behind while bus 0 is being serviced. **Resolved, and the design changed along the
way** -- the three findings below drove `core-integration/` off the buffered CAN stream session
entirely, onto the per-device `CAN`/`CANReceiveMessage` API it uses now. They're kept here as the
real history, since a project on a different WPILib build might still hit the same stream-session
bugs if it goes looking for that API instead.

**Finding 1 -- falling behind wasn't just lossy on the stream session, it crashed the JVM
outright.** The original `RioBridgeCan.poll()`'s `catch (CANStreamOverflowException)` was written
assuming ADR-0004's "a dropped frame is fine" story. It wasn't true for this specific exception: a
real overflow (an earlier version of `DiagnosticsRobot` left its own session unpolled for ~2.8s at
~220 frames/sec, far past its old 32-message buffer) segfaulted the whole JVM --
`SIGSEGV`/`SEGV_MAPERR` at address `0x0`, inside the native code that's supposed to construct and
throw `CANStreamOverflowException` in the first place (`wpi::hal::ThrowCANStreamOverflowException`
in `libwpiHaljni.so`, called from `CANJNI.readCANStreamSession`), before any Java `catch` block
ever got a chance to run. That's a genuine upstream bug in the stream session specifically, not
something fixable from this repo's code.

**Finding 2 -- a frame could also arrive malformed rather than missing entirely.** A run hit a
message that matched the Encoders arbitration ID with 0 bytes instead of the expected 8, which
crashed the whole robot program with an uncaught `IllegalArgumentException` out of
`CanFrames.unpackEncoders`.

**Finding 3 -- malformed frames turned out to be the *normal* case for the stream session, not a
rare edge case, and that's what actually explains Finding 2.** Once malformed frames were caught
and counted instead of left to crash the program, `malformedFrameCount` climbed at essentially the
protocol's entire combined send rate (~220/sec) while `attitudeFramesLastSecond` stayed stuck at
`0` -- not an occasional glitch, but every single frame. Root cause: `readCANStreamSession` never
marshaled payload bytes (`.data`/`.length`) back to Java at all on this WPILib build, while
`.timestamp`/`.messageId` came through correctly and consistently the whole time. This is a
genuine upstream bug, not fixable from this repo's code -- see
[`docs/wpilib-bug-report-can-stream-payload.md`](wpilib-bug-report-can-stream-payload.md) for the
full drafted bug report with the original evidence.

**The fix: move off the stream session API entirely.** `RioBridgeCan` now reads each frame on its
own API ID via the older, non-streaming, per-device `CAN`/`CANReceiveMessage` API --
`CANReceiveMessage.timestamp`'s own javadoc independently confirms microseconds too (item 1
above). **Confirmed working on real hardware**: deployed to a real SystemCore,
`malformedFrameCount` stayed at `0` and previously-stuck-at-0 frame counts came back nonzero,
across many consecutive one-second windows, driving a real swerve robot's gyro and encoders
through autonomous and teleop. Since there's no buffered session in this design, Finding 1's crash
mode structurally can't happen either -- `overflowCount()` is now always `0` by construction, kept
only so old callers built against the stream-session version of this class don't need an unrelated
code path removed. The tradeoff: no true per-sample buffering anymore, only the single latest
packet per API ID -- see `RioBridgeCan`'s class javadoc for what that costs and why it's an
accepted tradeoff for this project.

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
     RioBridgeCan: attitudeFramesLastSecond=50 (expect ~50 -- this loop's 50 Hz default period, not
     the Attitude frame's 100 Hz send rate: readPacketLatest has no buffering, so a 50 Hz poll of a
     100 Hz source structurally can't observe more than 50 distinct samples/sec) overflowCount=0
     malformedFrameCount=0
   ```

   **`attitudeFramesLastSecond` caps at ~50, not ~100, and that's correct** -- confirmed on real
   hardware landing right at that ceiling (consistently ~50-51/sec). See `DiagnosticsRobot`'s
   class javadoc for why: it's a structural consequence of polling a 100 Hz, non-buffered source
   at `TimedRobot`'s 50 Hz default period, not a regression or a sign anything's wrong.

**Pass criteria, all of these for the whole run:**

- `overflowCount` stays at `0`. Always true by construction now -- there's no buffered session
  left for this design to overflow (see the findings above) -- but kept as a pass criterion in
  case you're checking an older, stream-session-based version of this code instead.
- `malformedFrameCount` stays at `0`. A nonzero value means a frame arrived on one of the
  protocol's three API IDs but wasn't shaped like that frame's expected 8 bytes. Confirmed to
  happen at essentially 100% of frames on the old stream-session design (see the findings above);
  confirmed to stay at 0 on this per-device design across a real autonomous+teleop run. Any
  nonzero value here now is worth investigating as its own, new finding, not assumed to be the
  same already-diagnosed bug.
- `attitudeFramesLastSecond` stays close to 50, not 100 (see above) -- a few frames off from
  jitter is normal; a sustained drop meaningfully below 50 is a problem.
- Neither bus shows a `REGRESSED` flag -- i.e. `busOffCount`, `txFullCount`,
  `receiveErrorCount`, and `transmitErrorCount` never increase from one second to the next, on
  either bus.
- The process is still running at the end of the observation window at all.

**If it fails:** the fix is almost certainly on the Core side (reduce how much else shares the
SPI master, or how often you poll), not something to change in the RioBridge -- the RioBridge's
send rates are fixed by the protocol table and ADR-0004's explicit-sends design.

## 4. Encoder channels: onboard vs. MXP

**Resolved, on real hardware, and confirmed at the per-module level, not just onboard-vs-MXP in
the abstract.** `rio-bridge/`'s `Robot.java` used to just assume all four absolute encoders are on
onboard analog channels 0-3 (there was a `TODO` at that exact line). A real
`EncoderChannelDiagnostic` run, rotating each swerve module's encoder by hand in a known order
(FL, FR, BR, BL), confirmed:

- Each rotation produced exactly one onboard channel's smooth, large sweep, in this order:
  `onboard[0]` (FL), `onboard[1]` (FR), `onboard[3]` (BR), `onboard[2]` (BL).
- That's not just "all four on onboard, none on MXP" -- it's the *specific* channel assignment
  `NerdSwerveYAGSL2026`'s `Constants.java` assumes (`FRONT_LEFT`=0, `FRONT_RIGHT`=1, `BACK_LEFT`=2,
  `BACK_RIGHT`=3), confirmed to actually match, not just presumed to.

**A separate, non-blocking finding from the same run: real analog crosstalk.** While `onboard[3]`
(BACK_RIGHT) was sweeping, `MXP[4]` moved too -- proportionally, same direction, about 29% of
`onboard[3]`'s swing; `MXP[5]` moved more weakly (~8%); `MXP[6]` weaker still; `MXP[7]` stayed flat.
That falling-off-with-distance pattern (closest MXP channel most affected, farthest unaffected) is
consistent with capacitive crosstalk from an actively-driven, high-impedance encoder signal onto
adjacent floating channels -- not a wiring error, and not something that changes the channel
assignment above. Worth keeping in mind if a future addition to this project ever wires something
electrically sensitive to `MXP[4]`-`MXP[6]`, since it would pick up an echo of whatever's on
`onboard[3]`.

**Tool:** [`EncoderChannelDiagnostic`](../rio-bridge/src/main/java/frc/robot/diagnostics/EncoderChannelDiagnostic.java).
The steps below are kept as a runbook for re-verifying on different hardware (a different
harness/wiring run could plausibly differ), not because the answer is still open.

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

**Pass criteria:** each of the four encoders' *primary* signal -- the smooth, large sweep as you
rotate it -- shows up on a distinct `onboard[0]`-`onboard[3]` channel. `MXP[4]`-`MXP[7]` should
stay flat/noisy for three of the four rotations; a real run found one exception (see the
crosstalk finding above): rotating whichever encoder lands on `onboard[3]` can produce a smaller,
secondary echo on `MXP[4]`-`MXP[6]` that fades with distance. That's fine and doesn't fail this
check -- what would fail it is an *MXP* channel showing the primary, full-amplitude sweep instead
of an onboard one.

**If an encoder shows up on an MXP channel instead:** update `ENCODER_CHANNELS` in
`rio-bridge/src/main/java/frc/robot/Robot.java` to the real channel numbers (0-7, matching
`AnalogInput`'s own convention: 0-3 onboard, 4-7 MXP -- confirmed directly against the WPILib
2026.2.2 source while building this diagnostic).

**When done:** revert `Main.java` to `RobotBase.startRobot(Robot::new)` and redeploy the real
`Robot` before leaving the bench.

## 5. Core toolchain / vendor library build compatibility

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

**This repo settled on WPILib alpha-6, RioBridge included -- staying there was never actually
blocked the way an earlier detour to alpha-7 assumed.** `core-example-rev/README.md`'s "Using
WPILib alpha-6 instead" section has the mechanics (a year-frozen `release-2027` frcmaven repo
still serves alpha-4 through alpha-6 in full, and reproduced the exact same REVLib failure there
too -- ruling out an alpha-7-specific cause) and the corrected multi-bus story: alpha-6's friendly
`CAN`/`CANPort` API genuinely has no bus-selecting option via `CANPort` specifically (`CANPort`
doesn't exist yet at alpha-6), but `core-integration/`'s per-device `CAN` class takes a raw HAL bus
id (`int`) as its first constructor parameter directly, same as `CANJNI.openCANStreamSession` did
at either alpha -- just without `CANPort`'s enum wrapper around it
(`org.wpilib.hardware.hal.CANBusMap` has the same values as plain ints). Confirmed by actually
applying this repo's design to a real alpha-6 project, not just decompiling -- see that section,
and the root README's "Hardware this was designed against" for why alpha-6 (not alpha-7) is now
this repo's stated target: alpha-6 is what's actually confirmed end-to-end on real hardware, and
neither REV's nor CTRE's vendor libraries had shipped for alpha-7 anyway, so there was never a
compatibility reason to prefer the newer one.

**Steps, for whatever vendor library and framework your Core project actually uses:**

1. Open your Core project (or `clrozeboom/BobCat-SystemCore-Clone`'s `ctre-commands-v2`/
   `ctre-commands-v3` if you have no other yet).
2. Check what's actually pinned: the `org.wpilib.GradleRIO` plugin version in `build.gradle`, and
   each vendor library's version in `vendordeps/*.json`.
3. If you're checking compatibility with a WPILib version other than what's currently pinned,
   bump the WPILib/GradleRIO version (confirm what's current on frcmaven, since old 2027 alphas
   get overwritten in the normal `release` repo rather than retained -- `release-2027` keeps
   alpha-4 through alpha-6 regardless, see above) and try `./gradlew build`. Expect real compile
   errors if your code touches anything in `core-example-rev/README.md`'s alpha-6-vs-alpha-7 API
   differences table; fix those first so a subsequent failure is actually about vendor library
   compatibility, not a version-specific API change.
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
