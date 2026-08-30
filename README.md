# RioBridge

Republish a 2026 roboRIO's sensor interfaces onto a 2027 SystemCore CAN bus, so that sensors and
vendor libraries with no 2027 support stay readable from 2027 robot code.

**Status: design complete, implementation not started.**

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
  [BobcatRobotics/SystemCore-Clone](https://github.com/BobcatRobotics/SystemCore-Clone).
- **RioBridge** — roboRIO with a navX2 on the MXP port and four analog absolute encoders.
- **Robot** — swerve on REV SPARK MAX, PDH terminating CAN bus 0.

## Topology

```
roboRIO (RioBridge) ──CAN── HAT channel 1 (CANPort.CAN_S1) ──── Pi 5 (Core)

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

The Core reads every frame with a single buffered, timestamped stream session:

```java
CANJNI.openCANStreamSession(CANPort.CAN_S1.value, 0x0A080001, 0x1FFF003F, maxMessages);
```

The mask keeps device type, manufacturer and device number while ignoring the API bits, so one
session catches all three frames; demultiplex on `CANStreamMessage.messageId`. It also filters
out the RIO heartbeat for free, since that is device type 1.

## Core-side integration

`GyroIORioBridge implements GyroIO`, with `connected` driven by Attitude-frame staleness at a
100 ms threshold. Per-frame timestamps from the stream map onto AdvantageKit's
`odometryYawPositions[]` / `sampleTimestamps[]` pairing. On gyro loss the AdvantageKit
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

## Documents

- [CONTEXT.md](CONTEXT.md) — glossary. "RioBridge" and "Core" are the two ends; "RIO heartbeat"
  and "Status frame" are deliberately different things.
- [docs/adr/](docs/adr/) — the decisions and why.

## To verify before writing protocol code

1. `CANStreamMessage.timestamp` units — the field comment says milliseconds/`CLOCK_MONOTONIC`,
   the setter's javadoc says nanoseconds. Print raw values and find out.
2. HAT channel 1 termination jumper is actually set.
3. MCP2515 RX headroom at 100 Hz with bus 0 loaded — both HAT channels share the Pi's SPI master,
   so watch `getCANStatus` counters on both buses.
4. All four encoders are on onboard analog channels, none on MXP analog.
5. AdvantageKit alpha-4 and WPILib alpha-6 build together on the clone.
