package frc.robot.subsystems.drive.riobridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import frc.robot.protocol.CanFrames;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.can.CANReceiveMessage;

/** Exercises {@link RioBridgeCanDemux}'s {@code acceptX} methods against hand-built {@link CANReceiveMessage}s. */
class RioBridgeCanDemuxTest {
  /** Builds a {@link CANReceiveMessage} the way {@code acceptX} expects to receive one. */
  private static CANReceiveMessage message(byte[] payload, long timestampMicros) {
    CANReceiveMessage message = new CANReceiveMessage();
    byte[] buffer = message.setReceiveData(payload.length, 0, timestampMicros);
    System.arraycopy(payload, 0, buffer, 0, payload.length);
    return message;
  }

  @Test
  void statusFrameUpdatesLatestStatusAndItsTimestamp() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    byte[] payload =
        CanFrames.packStatus(42, 100, CanFrames.FLAG_NAVX_CONNECTED, CanFrames.PROTOCOL_VERSION);
    demux.acceptStatus(message(payload, 5_000_000));

    assertEquals(42, demux.latestStatus().loopCounter());
    assertTrue(demux.latestStatus().navxConnected());
    assertEquals(5.0, demux.latestStatusTimestampSeconds(), 1e-9);
  }

  @Test
  void encodersFrameUpdatesLatestEncoders() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    int[] raw = {10, 20, 30, 40};
    demux.acceptEncoders(message(CanFrames.packEncoders(raw), 1_000_000));

    assertEquals(10, demux.latestEncoders().rawCounts()[0]);
    assertEquals(40, demux.latestEncoders().rawCounts()[3]);
    assertEquals(1.0, demux.latestEncodersTimestampSeconds(), 1e-9);
  }

  @Test
  void distinctAttitudeTimestampsAccumulateInOrderUntilDrained() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    demux.acceptAttitude(message(CanFrames.packAttitude(1, 0, 0, 0), 0));
    demux.acceptAttitude(message(CanFrames.packAttitude(2, 0, 0, 0), 10_000));
    demux.acceptAttitude(message(CanFrames.packAttitude(3, 0, 0, 0), 20_000));

    assertEquals(3.0, demux.latestAttitude().attitude().yawDeg(), 1e-6);

    List<AttitudeSample> drained = demux.drainAttitudeSamples();
    assertEquals(3, drained.size());
    assertEquals(1.0, drained.get(0).attitude().yawDeg(), 1e-6);
    assertEquals(2.0, drained.get(1).attitude().yawDeg(), 1e-6);
    assertEquals(3.0, drained.get(2).attitude().yawDeg(), 1e-6);
    assertEquals(0.020, drained.get(2).timestampSeconds(), 1e-9);

    assertTrue(demux.drainAttitudeSamples().isEmpty(), "drain must empty the buffer");
  }

  @Test
  void repeatedAttitudeTimestampIsIgnoredRatherThanCountedAsANewSample() {
    // readPacketLatest returns the same cached packet on every call between real updates --
    // confirmed on real hardware, see RioBridgeCan's class javadoc. Polling it twice without a
    // new frame arriving must not produce two pending samples.
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    demux.acceptAttitude(message(CanFrames.packAttitude(1, 0, 0, 0), 10_000));
    demux.acceptAttitude(message(CanFrames.packAttitude(1, 0, 0, 0), 10_000));

    assertEquals(1, demux.drainAttitudeSamples().size());
  }

  @Test
  void malformedStatusFrameIsDroppedRatherThanThrowing() {
    // Confirmed possible on real hardware (see class javadoc): a message can arrive on the right
    // API ID with 0 bytes instead of the expected 8.
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    byte[] payload =
        CanFrames.packStatus(42, 100, CanFrames.FLAG_NAVX_CONNECTED, CanFrames.PROTOCOL_VERSION);
    demux.acceptStatus(message(payload, 1_000_000));

    demux.acceptStatus(message(new byte[0], 2_000_000));

    assertEquals(1, demux.malformedFrameCount());
    assertEquals(
        42,
        demux.latestStatus().loopCounter(),
        "the last good frame should be kept, not clobbered by the malformed one");
    assertTrue(
        demux.lastMalformedFrameDescription().contains("dataLength=0"),
        "the description should say what was actually wrong: " + demux.lastMalformedFrameDescription());
  }

  @Test
  void malformedEncodersFrameIsDroppedRatherThanThrowing() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    int[] raw = {10, 20, 30, 40};
    demux.acceptEncoders(message(CanFrames.packEncoders(raw), 1_000_000));

    demux.acceptEncoders(message(new byte[0], 2_000_000));

    assertEquals(1, demux.malformedFrameCount());
    assertEquals(10, demux.latestEncoders().rawCounts()[0]);
  }

  @Test
  void malformedAttitudeFrameIsDroppedRatherThanThrowingAndDoesNotAppearInDrain() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    demux.acceptAttitude(message(CanFrames.packAttitude(1, 0, 0, 0), 1_000_000));

    demux.acceptAttitude(message(new byte[0], 2_000_000));

    assertEquals(1, demux.malformedFrameCount());
    List<AttitudeSample> drained = demux.drainAttitudeSamples();
    assertEquals(1, drained.size(), "only the earlier good sample, not the malformed one");
    assertEquals(1.0, drained.get(0).attitude().yawDeg(), 1e-6);
  }

  @Test
  void neverAcceptedLeavesEverythingNull() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();

    assertNull(demux.latestStatus());
    assertNull(demux.latestEncoders());
    assertNull(demux.latestAttitude());
  }
}
