package frc.robot.subsystems.drive.riobridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import frc.robot.protocol.CanFrames;
import frc.robot.protocol.CanIds;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.can.CANJNI;
import org.wpilib.hardware.hal.can.CANStreamMessage;

/** Exercises {@link RioBridgeCanDemux#accept} against hand-built {@link CANStreamMessage}s. */
class RioBridgeCanDemuxTest {
  /** Builds a {@link CANStreamMessage} the way {@code accept} expects to receive one. */
  private static CANStreamMessage message(int messageId, byte[] payload, long timestampMicros) {
    CANStreamMessage message = new CANStreamMessage();
    byte[] buffer = message.setStreamData(payload.length, 0, messageId, timestampMicros);
    System.arraycopy(payload, 0, buffer, 0, payload.length);
    return message;
  }

  @Test
  void statusFrameUpdatesLatestStatusAndItsTimestamp() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    byte[] payload =
        CanFrames.packStatus(42, 100, CanFrames.FLAG_NAVX_CONNECTED, CanFrames.PROTOCOL_VERSION);
    demux.accept(message(CanIds.STATUS_ARBITRATION_ID, payload, 5_000_000));

    assertEquals(42, demux.latestStatus().loopCounter());
    assertTrue(demux.latestStatus().navxConnected());
    assertEquals(5.0, demux.latestStatusTimestampSeconds(), 1e-9);
  }

  @Test
  void encodersFrameUpdatesLatestEncoders() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    int[] raw = {10, 20, 30, 40};
    demux.accept(message(CanIds.ENCODERS_ARBITRATION_ID, CanFrames.packEncoders(raw), 1_000_000));

    assertEquals(10, demux.latestEncoders().rawCounts()[0]);
    assertEquals(40, demux.latestEncoders().rawCounts()[3]);
    assertEquals(1.0, demux.latestEncodersTimestampSeconds(), 1e-9);
  }

  @Test
  void attitudeFramesAccumulateInOrderUntilDrained() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    demux.accept(message(CanIds.ATTITUDE_ARBITRATION_ID, CanFrames.packAttitude(1, 0, 0, 0), 0));
    demux.accept(
        message(CanIds.ATTITUDE_ARBITRATION_ID, CanFrames.packAttitude(2, 0, 0, 0), 10_000));
    demux.accept(
        message(CanIds.ATTITUDE_ARBITRATION_ID, CanFrames.packAttitude(3, 0, 0, 0), 20_000));

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
  void frameTypeFlagBitsInMessageIdAreIgnoredWhenMatchingArbitrationId() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    int idWithExtendedFlag = CanIds.STATUS_ARBITRATION_ID | CANJNI.CAN_IS_FRAME_11BIT;
    demux.accept(message(idWithExtendedFlag, CanFrames.packStatus(7, 0, 0, 1), 0));

    assertEquals(7, demux.latestStatus().loopCounter());
  }

  @Test
  void unrecognizedMessageIdIsIgnoredRatherThanThrowing() {
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    demux.accept(message(0x1234, new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, 0));

    assertNull(demux.latestStatus());
    assertNull(demux.latestEncoders());
    assertNull(demux.latestAttitude());
  }

  @Test
  void malformedFrameOnARecognizedArbitrationIdIsDroppedRatherThanThrowing() {
    // Confirmed on real hardware: a message can match the Encoders arbitration ID with 0 bytes
    // instead of the expected 8 (see RioBridgeCanDemux's class javadoc).
    RioBridgeCanDemux demux = new RioBridgeCanDemux();
    int[] raw = {10, 20, 30, 40};
    demux.accept(message(CanIds.ENCODERS_ARBITRATION_ID, CanFrames.packEncoders(raw), 1_000_000));

    demux.accept(message(CanIds.ENCODERS_ARBITRATION_ID, new byte[0], 2_000_000));

    assertEquals(1, demux.malformedFrameCount());
    assertEquals(
        10,
        demux.latestEncoders().rawCounts()[0],
        "the last good frame should be kept, not clobbered by the malformed one");
    assertTrue(
        demux.lastMalformedFrameDescription().contains("dataLength=0"),
        "the description should say what was actually wrong: " + demux.lastMalformedFrameDescription());
  }
}
