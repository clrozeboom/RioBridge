# Draft bug report: `readCANStreamSession` never delivers payload bytes

Not filed yet — this is a draft for whoever files it (presumably against
[wpilibsuite/allwpilib](https://github.com/wpilibsuite/allwpilib/issues)) to review, edit, and
submit under their own account. Everything under "Evidence" is copied verbatim from real hardware
runs; nothing here is simulated or guessed.

---

## Title

`CAN.readCANStreamSession`/`CANStreamMessage` never populates `.data`/`.length` on SystemCore
(2027.0.0-alpha-6), while `.timestamp`/`.messageId` are populated correctly

## Environment

- WPILib: `2027.0.0-alpha-6` (`org.wpilib.hal:hal-java:2027.0.0-alpha-6`, matching
  `org.wpilib.GradleRIO` pinned to the same version)
- Platform: SystemCore (Raspberry Pi 5, `linux-aarch64`), `limelightosr-beta-11` OS build, JRE
  `Temurin-25.0.2+10`
- Not tested at alpha-7 or later — alpha-7 is the newest 2027 alpha available as of this writing
  (2026-09-05; confirmed against frcmaven, no alpha-8 exists yet), but this hasn't been deployed
  to real SystemCore hardware to check whether the same bug reproduces there. Alpha-7's own
  release notes mention only a `CANPort` enum change for CAN devices, nothing about the stream
  session or native/JNI fixes.

## Summary

Opening a CAN stream session (`CANJNI.openCANStreamSession` / the higher-level pattern of
polling it every loop) and reading it back with `CANJNI.readCANStreamSession` returns messages
whose `timestamp` and `messageId` fields are populated correctly and consistently, but whose
`data`/`length` fields are not — `length` reads `0` on essentially every message received, at a
rate matching the full rate of real traffic on the bus, not an occasional glitch.

## Reproduction

1. A device on one CAN bus explicitly sends a fixed-size (8-byte) payload at a known rate (in our
   case: three frame types at 20 Hz, 100 Hz, and 100 Hz respectively, ~220 messages/second
   combined) via `edu.wpi.first.wpilibj.CAN.writePacket` from a separate 2026 WPILib device (a
   roboRIO).
2. On the SystemCore side, open a stream session filtering for those messages:
   ```java
   int sessionHandle = CANJNI.openCANStreamSession(busId, arbitrationId, mask, maxMessages);
   ```
3. Poll it periodically (e.g. once per `TimedRobot` loop, ~every 20 ms):
   ```java
   CANStreamMessage[] scratch = new CANStreamMessage[maxMessages]; // pre-allocated, reused
   int messagesRead = CANJNI.readCANStreamSession(sessionHandle, scratch, scratch.length);
   for (int i = 0; i < messagesRead; i++) {
     CANStreamMessage m = scratch[i];
     // m.timestamp and m.messageId are correct.
     // m.length is 0. Arrays.copyOf(m.data, m.length) is therefore always a 0-length array,
     // regardless of the real 8-byte payload actually on the bus.
   }
   ```
4. Any code that expects `m.length` to match the real payload size (here, 8 bytes) throws
   immediately on the first read that actually returns a message.

## Expected behavior

`CANStreamMessage.data`/`.length` reflect the real payload bytes and length of each received CAN
frame, the same way `.timestamp` and `.messageId` do.

## Actual behavior

`.length` is consistently `0` (payload effectively discarded) while `.timestamp` and `.messageId`
are correct and internally consistent (timestamps increase steadily at the expected rate;
`messageId` values match the real arbitration IDs actually being sent). This has been reproduced
on every deploy since bring-up began, at a rate matching essentially 100% of real traffic — not
an intermittent or rare condition.

## Evidence (verbatim from real hardware)

**A single instance, from the point this was first caught** (before defensive handling was
added — this was an unhandled exception that crashed the whole robot program):

```
Error at frc.robot.protocol.CanFrames.requireLength(CanFrames.java:142): Unhandled exception: java.lang.IllegalArgumentException: expected 8 bytes, got 0
  at frc.robot.protocol.CanFrames.requireLength(CanFrames.java:142)
  at frc.robot.protocol.CanFrames.unpackEncoders(CanFrames.java:80)
  at frc.robot.subsystems.drive.riobridge.RioBridgeCanDemux.accept(RioBridgeCanDemux.java:51)
  at frc.robot.subsystems.drive.riobridge.RioBridgeCan.poll(RioBridgeCan.java:96)
  at frc.robot.subsystems.drive.riobridge.diagnostics.DiagnosticsRobot.robotPeriodic(DiagnosticsRobot.java:84)
  at org.wpilib.framework.IterativeRobotBase.loopFunc(IterativeRobotBase.java:318)
  ...
```

**After adding defensive handling (catch and count, don't crash), the sustained rate over
several one-second windows**, printed once per second:

```
RioBridgeCan: attitudeFramesLastSecond=0 (expect ~100 at 100 Hz) overflowCount=0 malformedFrameCount=16036  <-- frames have been DROPPED or discarded, not just delayed
RioBridgeCan: attitudeFramesLastSecond=0 (expect ~100 at 100 Hz) overflowCount=0 malformedFrameCount=16256  <-- ...
RioBridgeCan: attitudeFramesLastSecond=0 (expect ~100 at 100 Hz) overflowCount=0 malformedFrameCount=16476  <-- ...
RioBridgeCan: attitudeFramesLastSecond=0 (expect ~100 at 100 Hz) overflowCount=0 malformedFrameCount=16700  <-- ...
```

Deltas between consecutive seconds: 220, 220, 224 — matching the real combined send rate (20 Hz +
100 Hz + 100 Hz = 220/sec) almost exactly. `attitudeFramesLastSecond=0` for the entire run: not
one of the ~100 Attitude frames sent per second was ever successfully unpacked.

**Per-message detail, logged once per second (most recent malformed message that window)** —
note the arbitration ID is correct (`0x0A080441`, our real Attitude frame's ID) and the raw
timestamp increases by ~1,000,000–1,020,000 (~1 second, in microseconds) between each log line,
consistent with `.timestamp` being populated correctly and continuously the whole time:

```
last malformed frame: arbitrationId=0x0A080441 rawMessageId=0x0A080441 dataLength=0 rawTimestamp=4910987798: expected 8 bytes, got 0
last malformed frame: arbitrationId=0x0A080441 rawMessageId=0x0A080441 dataLength=0 rawTimestamp=4911997821: expected 8 bytes, got 0
last malformed frame: arbitrationId=0x0A080441 rawMessageId=0x0A080441 dataLength=0 rawTimestamp=4913017929: expected 8 bytes, got 0
last malformed frame: arbitrationId=0x0A080441 rawMessageId=0x0A080441 dataLength=0 rawTimestamp=4914017920: expected 8 bytes, got 0
```

## A second, separate finding in the same native code path

While debugging the above, we also hit a JVM-level segfault (not a Java exception — the JVM
itself crashed) when the stream session's buffer genuinely overflowed (left unpolled for ~2.8
seconds while receiving ~220 messages/second, far exceeding a 32-message buffer). The crash
report (`hs_err_pid*.log`) showed:

```
SIGSEGV (0xb), si_code: 1 (SEGV_MAPERR), si_addr: 0x0
Problematic frame: V  [libjvm.so+0x9879b8]

Native frames:
C  [libwpiHaljni.so+0x2a950]  JNIEnv_::NewObject(_jclass*, _jmethodID*, ...)+0x70
C  [libwpiHaljni.so+0x38fa8]  wpi::hal::ThrowCANStreamOverflowException(JNIEnv_*, _jobjectArray*, int)+0x88
C  [libwpiHaljni.so+0x3c6ac]  Java_org_wpilib_hardware_hal_can_CANJNI_readCANStreamSession+0x27c
J  org.wpilib.hardware.hal.can.CANJNI.readCANStreamSession(...)
j  frc.robot.subsystems.drive.riobridge.RioBridgeCan.poll()
j  frc.robot.subsystems.drive.riobridge.diagnostics.DiagnosticsRobot.robotPeriodic()
```

`wpi::hal::ThrowCANStreamOverflowException`'s call to `JNIEnv_::NewObject` null-derefs while
constructing the `CANStreamOverflowException` object it's supposed to throw back to Java — so a
genuine buffer overflow crashes the whole JVM instead of the exception being catchable. We worked
around this by sizing the buffer generously and never leaving a session open-but-unpolled for
long, but the underlying null-deref in `ThrowCANStreamOverflowException` is a separate bug from
the payload-marshaling one above and worth reporting alongside it, since it's the same native
translation unit.

## Suspected root cause (not confirmed — we have no access to the native/C++ source)

The native implementation of `readCANStreamSession` (or whatever underlying buffer-copy routine
feeds `CANStreamMessage.data`/`.length`) appears to correctly forward scalar fields
(`timestamp`, `messageId`) but not the variable-length payload copy into the pre-existing Java
`byte[] data` array on each `CANStreamMessage` object. Given the overflow-handling bug above is
also in this same function's error path, it's plausible both stem from the same underlying
native routine being incompletely ported/tested for this platform (SystemCore/Linux SocketCAN)
rather than an issue specific to either bug in isolation.

## Workaround in use

We moved off the stream session API entirely for our use case, to the older per-device API
(`CANAPIJNI.initializeCAN` + `readCANPacketLatest`/`CANReceiveMessage`, wrapped by
`org.wpilib.hardware.bus.CAN`). Whether that API correctly marshals payload bytes on this same
platform is itself still being verified as of this writing — if it turns out to have the same
gap, that would suggest the bug is broader than just the stream session's implementation.

## Additional context

This was found while building
[clrozeboom/RioBridge](https://github.com/clrozeboom/RioBridge) — a project that republishes a
2026 roboRIO's sensors over CAN to a 2027 SystemCore. Full context, including the timestamp-unit
ambiguity also found in `CANStreamMessage` (separately confirmed as microseconds, undocumented
correctly in either of that class's own conflicting javadoc comments) is in that repo's
`docs/hardware-verification.md` and `core-integration/src/main/java/frc/robot/subsystems/drive/riobridge/RioBridgeCan.java`'s
class javadoc.
