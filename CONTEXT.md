# RioBridge

A 2026 roboRIO republishes its sensor interfaces onto a 2027 SystemCore CAN bus, so that
sensors and vendor libraries with no 2027 support stay readable from 2027 robot code.

## Language

### The two ends

**RioBridge**:
A roboRIO running a purpose-built 2026 project whose only job is to read sensors and publish
them as CAN frames. Distinct from "roboRIO", which names the hardware regardless of role.
_Avoid_: Bridge, the Rio, sensor node, I/O expander

**Core**:
The 2027 controller that consumes RioBridge frames. Here, a Raspberry Pi 5 with a dual-channel
CAN HAT running SystemCore OS and WPILib 2027. Say "real SystemCore" when the distinction from
FIRST's hardware matters.
_Avoid_: SystemCore (ambiguous in this repo), the Pi, coprocessor, host

**RioBridge bus**:
The CAN segment carrying only the Core and the RioBridge. Physically separate from the bus
carrying motor controllers.
_Avoid_: bus 1, CAN1, the second bus

### What travels

**Signal**:
One named readable value originating at the RioBridge, such as an analog channel or a gyro axis.
_Avoid_: input, reading, datapoint, measurement

**Frame**:
One CAN frame carrying a fixed group of signals. The grouping is part of the protocol, not
chosen per send.
_Avoid_: packet, message

**Fast frame**:
A frame published at the rate used for signals whose freshness affects control, such as gyro
angle and absolute encoder position.

**Slow frame**:
A frame published at the rate used for signals that only need to be current, such as digital
inputs and diagnostics.

**Status frame**:
The RioBridge's own liveness and health frame, carrying its sequence counter and which sensors
it believes are present. Ours to design.
_Avoid_: heartbeat, keepalive, watchdog frame

**RIO heartbeat**:
The FRC broadcast a roboRIO emits every 20 ms whether or not anyone wants it, which FRC actuator
devices use to determine robot enable state. Never refers to the Status frame.
_Avoid_: heartbeat (unqualified)

### Encoder handling

**Seed**:
Setting a swerve module's relative encoder from its absolute encoder reading, so the relative
encoder knows true azimuth.

**Reseed**:
Repeating the seed continuously while the robot runs, rather than only at startup.
