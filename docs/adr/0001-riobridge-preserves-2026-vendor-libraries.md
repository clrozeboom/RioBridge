# RioBridge exists to preserve unmodified 2026 vendor libraries

SystemCore drops the MXP header, SPI, analog output and most of the roboRIO's I/O, and vendors
have not shipped 2027 libraries for sensors such as the navX. Rather than port or fork each
library, we run the sensors on a roboRIO under its working 2026 vendor libraries and republish
their outputs over CAN. The value is not I/O expansion in itself: it is that no vendor library
has to be modified, so anything a 2026 roboRIO can read stays readable from 2027 code.

## Considered Options

- **Point fixes.** The analog absolute encoders could go into the SPARK MAX data port's analog
  input (pin 3, 12-bit, with REV's breakout board converting 5 V down to the port's 3.3 V range),
  and the navX could stream over USB serial — WPILib 2027 does have
  `org.wpilib.hardware.bus.SerialPort` with `Port.USB`, and the navX Java library is pure Java,
  so that path is a fork-and-repackage rather than a rewrite. Rejected: the breakout boards are a
  purchase, the navX path leaves us owning a fork of someone else's vendor library, and neither
  generalises past these two specific sensors.
- **Wait for vendors.** Rejected: the gap being filled is pre-season testing, which is exactly
  the window in which the 2027 libraries do not yet exist.

## Consequences

The RioBridge is only as capable as 2026 WPILib and inherits its constraints — including the RIO
heartbeat it cannot suppress, which is why ADR-0002 exists.
