# RioBridge

Republish a 2026 roboRIO's sensor interfaces onto a 2027 SystemCore CAN bus, so that sensors and
vendor libraries with no 2027 support stay readable from 2027 robot code.

**Status: confirmed working end-to-end on real hardware** — roboRIO to Core to a real swerve
robot, with live gyro and encoder data and working autonomous routines — via this design applied
to [clrozeboom/NerdSwerveYAGSL2026](https://github.com/clrozeboom/NerdSwerveYAGSL2026). See
[Implementation](#implementation) for exactly what that covers directly in this repo versus by
that port, and what's still open.

## Why

SystemCore drops the MXP header, SPI, analog output, relays and most of the roboRIO's I/O — it
has six reconfigurable Smart I/O ports in total. Vendors have not yet shipped 2027 libraries for
sensors such as the navX. During pre-season testing that leaves teams holding working hardware
they cannot read.

RioBridge runs those sensors on a roboRIO under its **unmodified 2026 vendor libraries** and
republishes their values over CAN. Nothing gets forked or ported. See
[ADR-0001](docs/adr/0001-riobridge-preserves-2026-vendor-libraries.md) for the alternatives that
were considered and rejected.

## Hardware this was designed against

- **Core** — Raspberry Pi 5 + dual-channel Waveshare CAN HAT (no-FD), running Limelight
  SystemCore OS with WPILib `2027.0.0-alpha-6` and AdvantageKit `v27.0.0-alpha-4`. Built from
  [BobcatRobotics/SystemCore-Clone](https://github.com/BobcatRobotics/SystemCore-Clone). A brief
  detour to `alpha-7` turned out not to be required — alpha-6 was never actually blocked from
  frcmaven the way that detour assumed (see `core-example-rev/README.md`'s "Using WPILib alpha-6
  instead"), and alpha-6 is the version actually confirmed end-to-end on real hardware. The
  alpha-7 exploration itself wasn't wasted — see the `claude/mattpocock-skills-plan-j0a909` branch
  of this repo — and stays available if a future vendor library or WPILib feature needs it.
- **RioBridge** — roboRIO with a navX2 on the MXP port and four analog absolute encoders.
- **Robot** — swerve on REV SPARK MAX, PDH terminating CAN bus 0.

## Topology

```
roboRIO (RioBridge) ──CAN── HAT channel 1 (CAN_S1) ──── Pi 5 (Core)

CAN bus 0: SPARK MAXes + PDH                    (untouched)
CAN bus 1: Core + RioBridge only
```

The RioBridge gets a bus to itself. A roboRIO running FRC robot code transmits the RIO heartbeat
at arbitration ID `0x01011840` every 20 ms unconditionally, and FRC actuator devices are required
to key their enable state off it — two heartbeat sources on one bus means motor controllers
receiving contradictory enable state. See
[ADR-0002](docs/adr/0002-dedicated-riobridge-bus.md).

Termination needs no extra parts: the roboRIO has an internal 120 Ohm resistor across CAN L and
H, and the HAT channel terminates the other end.

## Protocol

Device number 1, device type `MISCELLANEOUS` (10), manufacturer `TEAM_USE` (8), giving a base
arbitration ID of `0x0A080000`. Little-endian. Every frame is sent explicitly from the RioBridge
loop and never with `writePacketRepeating`, so that a frame arriving is evidence the loop ran —
see [ADR-0004](docs/adr/0004-explicit-sends-not-writepacketrepeating.md).

| Frame | Arbitration ID | Rate | Bytes 0-1 | 2-3 | 4-5 | 6-7 |
|---|---|---|---|---|---|---|
| Status | `0x0A080001` | 20 Hz | loopCounter u16 | uptime u16 | flags | protocolVersion |
| Encoders | `0x0A080401` | 100 Hz | analog[0] u16 | analog[1] | analog[2] | analog[3] |
| Attitude | `0x0A080441` | 100 Hz | yaw i16 @0.01 deg | yawRate i16 @0.1 deg/s | pitch i16 @0.01 deg | roll i16 @0.01 deg |

Encoder frames carry **raw 12-bit ADC counts**, not angles: offsets, inversions and re-zeroing
stay on the Core where the swerve configuration already lives, so re-zeroing a module never means
reflashing the RioBridge.

The Core reads each frame on its own API ID via the per-device `CAN`/`CANReceiveMessage` API,
not a buffered stream session — an earlier design here used a single stream session filtered by
device identity (matching device type, manufacturer and device number while ignoring the API
bits, so one session would catch all three frames and filter out the RIO heartbeat for free), but
that turned out to have a real, confirmed WPILib bug: `readCANStreamSession` never marshals
payload bytes back to Java at all, only `.timestamp`/`.messageId` (see
[docs/wpilib-bug-report-can-stream-payload.md](docs/wpilib-bug-report-can-stream-payload.md)).
The per-device API doesn't share that bug:

```java
CAN can = new CAN(CANBusMap.CAN_S1, CanIds.DEVICE_NUMBER, CAN.TEAM_MANUFACTURER, CAN.TEAM_DEVICE_TYPE);
CANReceiveMessage message = new CANReceiveMessage();
can.readPacketLatest(CanIds.STATUS_API_ID, message); // one call per frame type, each on its own API ID
```

Confirmed working on real hardware — see `core-integration/README.md`. The tradeoff is losing
true per-sample buffering: `readPacketLatest` only ever returns the single most recent packet per
API ID, so a caller polling faster than the sender can only ever see the latest value, not every
sample sent in between (see `RioBridgeCan`'s class javadoc for what that costs and why it's an
accepted tradeoff here).

## Core-side integration

`GyroIORioBridge implements GyroIO`, with `connected` driven by Attitude-frame staleness at a
100 ms threshold. Per-frame timestamps map onto AdvantageKit's `odometryYawPositions[]` /
`sampleTimestamps[]` pairing — expect 0 or 1 new samples per loop now, not several, since the
per-device CAN API above has no per-sample buffering (a real, accepted loss of resolution versus
the original buffered-stream design; see `RioBridgeCan`'s class javadoc). On gyro loss the AdvantageKit
`spark_swerve` template's existing fallback applies — heading integrated from
`kinematics.toTwist2d(moduleDeltas)`, with its disconnect alert — so the driver keeps
field-relative control rather than being switched to a different scheme mid-run.

## Scope

**In:** reading four analog absolute encoders and navX attitude from a 2027 codebase.

**Out:** outputs ([ADR-0003](docs/adr/0003-riobridge-is-read-only.md)), DIO, quaternion,
competition legality, real SystemCore hardware.

The reliability bar is "good enough to test things" — this is an offseason robot that will not be
played in a match. See [ADR-0005](docs/adr/0005-offseason-reliability-bar.md), which records what
was deliberately left out on those grounds.

## Implementation

- [rio-bridge/](rio-bridge/) — the roboRIO-side WPILib project: reads the sensors, sends the
  three frames explicitly each loop. Builds and its tests pass against the real 2026.2.2 WPILib
  jars and the real navX vendor jar (`vendordeps/Studica.json`) — see `rio-bridge/README.md`. This
  exact code is what ran on the real RioBridge roboRIO in the hardware run above.
- [core-integration/](core-integration/) — drop-in files for the Core's project: the per-device
  CAN reads (not a stream session — see "Protocol" above) and a `GyroIO` implementation. Builds
  and its tests pass against the real `2027.0.0-alpha-6` `org.wpilib` jars, this repo's stated
  WPILib target. This exact design — not this exact copy of the files — is what's confirmed on
  real hardware: it was hand-ported to a real alpha-6-pinned robot project
  ([clrozeboom/NerdSwerveYAGSL2026](https://github.com/clrozeboom/NerdSwerveYAGSL2026)) and drove
  a real swerve robot's gyro and encoders in autonomous and teleop. See `core-integration/README.md`
  for exactly what that does and doesn't cover for this copy of the files, and what's still open
  on the "to verify" list below.
- [core-example-rev/](core-example-rev/) — a standalone REVLib + alpha-7 smoke test, *not* wired
  to the RioBridge protocol and no longer this repo's stated Core target (see above). Confirms
  REVLib is in the same spot as CTRE Phoenix6 (both still published at alpha-6, contrary to what
  prompted building this) and that everything up to the native driver actually loading works
  against alpha-7 — the one thing that doesn't is a REV packaging gap, not anything here, and it
  reproduces the same way regardless of command framework (tried against both `command2` and
  `command3`). See `core-example-rev/README.md`, including a table of real API differences it
  found between alpha-6 and alpha-7 along the way -- useful if a future vendor library or WPILib
  feature pulls this repo back to alpha-7.

`rio-bridge/` and `core-integration/`'s *design* is confirmed running end-to-end on real hardware
(see the Status line above); `core-example-rev/` has not run against real SystemCore hardware —
see its README for exactly what "builds and tests pass" does and doesn't cover.

## Documents

- [CONTEXT.md](CONTEXT.md) — glossary. "RioBridge" and "Core" are the two ends; "RIO heartbeat"
  and "Status frame" are deliberately different things.
- [docs/adr/](docs/adr/) — the decisions and why.

## To verify before running on hardware

Step-by-step procedures for all of these, including runnable diagnostics for items 1, 3 and 4,
are in [docs/hardware-verification.md](docs/hardware-verification.md).

1. ~~`CANStreamMessage.timestamp` units~~ — **resolved**: the field comment on the real WPILib
   source says milliseconds/`CLOCK_MONOTONIC`, `setStreamData`'s parameter javadoc on the *same
   class* says nanoseconds -- neither was right. A real `TimestampUnitsCheck` run against real
   hardware measured `secondsPerUnit ~= 1e-6` (wall-clock elapsed=1.946s against a raw timestamp
   delta of 1,950,175 over 40 Status frames at 20 Hz): **microseconds** — and
   `CANReceiveMessage.timestamp` (the per-device API `core-integration/` reads frames through now,
   see "Protocol" above) documents microseconds unambiguously in its own javadoc, confirming the
   same unit two different ways. `core-integration/`'s `RioBridgeCanDemux` scales by that. See
   [docs/hardware-verification.md](docs/hardware-verification.md) item 1.
2. ~~HAT channel 1 termination jumper is actually set~~ — **resolved**: measured ~60 Ohms across
   CAN_H/CAN_L with the RioBridge and Core's CAN_S1 connected end-to-end, confirming both the
   roboRIO's internal terminator and the HAT channel 1 terminator are present and in circuit
   together. See [docs/hardware-verification.md](docs/hardware-verification.md) item 2.
3. ~~MCP2515 RX headroom at 100 Hz with bus 0 loaded~~ — **resolved, and the design changed along
   the way**: both HAT channels share the Pi's SPI master, so the original question was whether
   `getCANStatus` counters on either bus ever regress under load. That's still worth watching, but
   the original design's real risk here was worse than lost headroom: a buffered CAN stream
   session overflowing crashed the JVM outright, via a genuine native bug in `readCANStreamSession`
   (`CANStreamOverflowException` segfaults while being constructed, before any Java `catch` runs).
   `core-integration/` no longer uses that stream session at all (see "Protocol" above) — the
   per-device API it reads frames through now has no buffered session to overflow, so that specific
   crash mode doesn't apply to the current design. Confirmed on real hardware: `overflowCount`
   stayed at 0 (trivially — there's nothing left to overflow) and bus counters showed no
   regression across a real autonomous+teleop run. See `RioBridgeCan`'s class javadoc and
   [docs/hardware-verification.md](docs/hardware-verification.md) item 3.
4. All four encoders are on onboard analog channels, none on MXP analog. `rio-bridge/`'s encoder
   channel list has a `TODO` at this exact point.
5. ~~AdvantageKit alpha-4 builds against WPILib alpha-6~~ — **resolved**: this repo briefly moved
   its stated Core target to alpha-7 on the mistaken assumption that alpha-6 was no longer
   resolvable from frcmaven at all (see the "Hardware this was designed against" section's "brief
   detour" and `core-example-rev/README.md`'s "Using WPILib alpha-6 instead" for the correction),
   which would have left this pairing unverified by construction — `core-integration/` itself has
   no AdvantageKit dependency to be wrong about.
   Back on alpha-6, the pairing is now directly confirmed, not just assumed: `akit-java:27.0.0-alpha-4`
   against `org.wpilib.GradleRIO`/WPILib `2027.0.0-alpha-6` is the exact combination
   `clrozeboom/NerdSwerveYAGSL2026` builds and runs with on real hardware.
6. ~~`NavxAttitudeSource`'s exact navX2 constructor call (`NavXComType.kMXP_SPI`) against whichever
   navX vendordep version you install~~ — **resolved**: `vendordeps/Studica.json` pins
   `com.studica.frc:Studica-java:2026.0.0`, and `NavXComType.kMXP_SPI` is confirmed correct
   against its real source, including a HAL-sim run that logs a real navX MXP connection
   sequence. See `rio-bridge/README.md`.
