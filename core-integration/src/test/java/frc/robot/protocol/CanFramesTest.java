package frc.robot.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import frc.robot.protocol.CanFrames.AttitudeFrame;
import frc.robot.protocol.CanFrames.EncodersFrame;
import frc.robot.protocol.CanFrames.StatusFrame;
import org.junit.jupiter.api.Test;

class CanFramesTest {
  @Test
  void statusRoundTrips() {
    byte[] wire = CanFrames.packStatus(1234, 6789, CanFrames.FLAG_NAVX_CONNECTED, CanFrames.PROTOCOL_VERSION);
    assertEquals(8, wire.length);

    StatusFrame decoded = CanFrames.unpackStatus(wire);
    assertEquals(1234, decoded.loopCounter());
    assertEquals(6789, decoded.uptimeSeconds());
    assertEquals(CanFrames.PROTOCOL_VERSION, decoded.protocolVersion());
    assertTrue(decoded.navxConnected());
  }

  @Test
  void statusNavxDisconnectedFlagIsFalseWhenBitUnset() {
    StatusFrame decoded = CanFrames.unpackStatus(CanFrames.packStatus(0, 0, 0, 1));
    assertFalse(decoded.navxConnected());
  }

  @Test
  void statusFieldsWrapAtU16RatherThanThrowing() {
    // loopCounter and uptime are free-running counters that are expected to wrap; packing must
    // truncate silently rather than throw out of the RioBridge main loop.
    StatusFrame decoded = CanFrames.unpackStatus(CanFrames.packStatus(70_000, -1, 0, 1));
    assertEquals(70_000 - 65536, decoded.loopCounter());
    assertEquals(65535, decoded.uptimeSeconds());
  }

  @Test
  void statusIsLittleEndianOnWire() {
    byte[] wire = CanFrames.packStatus(0x0102, 0, 0, 0);
    assertEquals(0x02, wire[0]);
    assertEquals(0x01, wire[1]);
  }

  @Test
  void encodersRoundTrip() {
    int[] raw = {0, 4095, 2048, 1};
    EncodersFrame decoded = CanFrames.unpackEncoders(CanFrames.packEncoders(raw));
    assertArrayEquals(raw, decoded.rawCounts());
  }

  @Test
  void encodersRejectsWrongChannelCount() {
    assertThrows(IllegalArgumentException.class, () -> CanFrames.packEncoders(new int[] {1, 2, 3}));
  }

  @Test
  void attitudeRoundTripsWithinLsbRounding() {
    AttitudeFrame decoded =
        CanFrames.unpackAttitude(CanFrames.packAttitude(-179.99, 250.4, 12.34, -45.67));
    assertEquals(-179.99, decoded.yawDeg(), 0.005);
    assertEquals(250.4, decoded.yawRateDegPerSec(), 0.05);
    assertEquals(12.34, decoded.pitchDeg(), 0.005);
    assertEquals(-45.67, decoded.rollDeg(), 0.005);
  }

  @Test
  void attitudeClampsRatherThanOverflowing() {
    // Yaw rate LSB is 0.1 deg/s, so the i16 range is +/-3276.7 deg/s -- comfortably above any
    // real navX rate, but packing must clamp, not wrap, if it's ever exceeded.
    AttitudeFrame decoded = CanFrames.unpackAttitude(CanFrames.packAttitude(0, 100_000, 0, 0));
    assertEquals(3276.7, decoded.yawRateDegPerSec(), 0.05);
  }

  @Test
  void framesRoundTripThroughWireLength() {
    assertThrows(IllegalArgumentException.class, () -> CanFrames.unpackStatus(new byte[7]));
    assertThrows(IllegalArgumentException.class, () -> CanFrames.unpackEncoders(new byte[9]));
    assertThrows(IllegalArgumentException.class, () -> CanFrames.unpackAttitude(new byte[0]));
  }
}
