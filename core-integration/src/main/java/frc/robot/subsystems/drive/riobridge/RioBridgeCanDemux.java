package frc.robot.subsystems.drive.riobridge;

import frc.robot.protocol.CanFrames;
import frc.robot.protocol.CanFrames.EncodersFrame;
import frc.robot.protocol.CanFrames.StatusFrame;
import frc.robot.protocol.CanIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.wpilib.hardware.hal.can.CANReceiveMessage;

/**
 * The RioBridge protocol's decode state: turns a {@link CANReceiveMessage} already known to be a
 * given frame type (see {@link RioBridgeCan#poll()}, which reads each frame on its own API ID)
 * into that frame's typed value. Deliberately has no JNI in it -- {@link CANReceiveMessage} is a
 * plain field holder ({@code setReceiveData} exists so JNI can populate one, but nothing stops
 * test code from calling it directly) -- so this class is exercised in {@code
 * RioBridgeCanDemuxTest} without a CAN session or the desktop HAL sim, same as before.
 *
 * <p><b>No message-ID dispatch here anymore.</b> The previous, stream-session-based version of
 * this class matched an incoming {@code CANStreamMessage.messageId} against the three RioBridge
 * arbitration IDs to decide which frame it was. {@link RioBridgeCan} now reads each frame on its
 * own API ID via {@code CAN.readPacketLatest(apiId, message)} -- the frame type is already known
 * by which {@code acceptX} method gets called, so there's nothing left to demux by ID.
 *
 * <p><b>{@code readPacketLatest} returns the same cached packet on every call between real
 * updates</b> -- confirmed on real hardware (see {@link RioBridgeCan}'s class javadoc). For
 * Status/Encoders that's harmless: {@code latestStatus()}/{@code latestEncoders()} are meant to
 * read as "whatever's most recent," and staleness is the caller's concern (e.g. {@code
 * GyroIORioBridge}'s own threshold). For Attitude it matters: {@link #acceptAttitude} only treats
 * a reading as a new sample -- i.e. only adds it to {@link #drainAttitudeSamples()} -- when its
 * timestamp actually moved since the last one accepted, the same dedup {@code RioBridgeCan}'s
 * NerdSwerveYAGSL2026 counterpart uses. Since {@link RioBridgeCan#poll()} and whatever drains
 * this each run once per robot loop, this will hold at most one pending sample at a time in
 * practice -- {@link #drainAttitudeSamples()} still returns a {@code List} (matching the previous
 * multi-sample-per-poll API so {@code GyroIORioBridge} needed no changes), but a caller polling a
 * 100 Hz Attitude frame at {@code TimedRobot}'s 50 Hz default period should expect it to hold 0 or
 * 1 elements per call, not several -- a real, accepted loss of resolution against the old buffered
 * stream session's true multi-sample capture, for the same reason {@code RioBridgeCan}'s class
 * javadoc gives.
 *
 * <p>{@link CANReceiveMessage#timestamp}'s javadoc is unambiguous -- "Timestamp message was
 * received, in microseconds (wpi time)" -- so {@link #TIMESTAMP_TO_SECONDS} carries over
 * unchanged from the stream-session version, which needed a real hardware run to resolve the same
 * question against {@code CANStreamMessage}'s self-contradicting javadoc (see {@code
 * TimestampUnitsCheck}).
 *
 * <p><b>A message can still come back malformed -- confirmed on real hardware, and the reason this
 * whole class exists in this shape rather than trusting {@code CanFrames.unpackX} directly.</b>
 * The previous, stream-session-based {@code RioBridgeCan} hit this at essentially the protocol's
 * entire combined frame rate (~220/sec), not an occasional glitch -- that turned out to be a
 * genuine upstream bug specific to {@code readCANStreamSession} never marshaling payload bytes at
 * all (see {@code RioBridgeCan}'s class javadoc and {@code docs/wpilib-bug-report-can-stream-payload.md}),
 * which is exactly why {@link RioBridgeCan} moved off that API. This class keeps the same
 * defensive shape regardless -- drop a frame that fails to unpack instead of letting the {@code
 * IllegalArgumentException} escape and kill the whole robot program -- since a malformed frame is
 * cheap to guard against and there's no guarantee some other, not-yet-found rough edge in the new
 * API couldn't produce one too.
 */
final class RioBridgeCanDemux {
  private static final double TIMESTAMP_TO_SECONDS = 1.0 / 1_000_000.0;

  private StatusFrame latestStatus;
  private double latestStatusTimestampSeconds = Double.NEGATIVE_INFINITY;
  private EncodersFrame latestEncoders;
  private double latestEncodersTimestampSeconds = Double.NEGATIVE_INFINITY;
  private AttitudeSample latestAttitude;
  private double lastSeenAttitudeTimestampSeconds = Double.NEGATIVE_INFINITY;
  private final List<AttitudeSample> pendingAttitudeSamples = new ArrayList<>();
  private int malformedFrameCount = 0;
  private String lastMalformedFrameDescription;

  void acceptStatus(CANReceiveMessage message) {
    try {
      latestStatus = CanFrames.unpackStatus(trim(message));
      latestStatusTimestampSeconds = message.timestamp * TIMESTAMP_TO_SECONDS;
    } catch (IllegalArgumentException malformed) {
      recordMalformed(CanIds.STATUS_API_ID, message, malformed);
    }
  }

  void acceptEncoders(CANReceiveMessage message) {
    try {
      latestEncoders = CanFrames.unpackEncoders(trim(message));
      latestEncodersTimestampSeconds = message.timestamp * TIMESTAMP_TO_SECONDS;
    } catch (IllegalArgumentException malformed) {
      recordMalformed(CanIds.ENCODERS_API_ID, message, malformed);
    }
  }

  void acceptAttitude(CANReceiveMessage message) {
    double timestampSeconds = message.timestamp * TIMESTAMP_TO_SECONDS;
    if (timestampSeconds == lastSeenAttitudeTimestampSeconds) {
      return; // Same cached packet as last poll -- not a new sample from the RioBridge.
    }
    try {
      AttitudeSample sample =
          new AttitudeSample(CanFrames.unpackAttitude(trim(message)), timestampSeconds);
      lastSeenAttitudeTimestampSeconds = timestampSeconds;
      latestAttitude = sample;
      pendingAttitudeSamples.add(sample);
    } catch (IllegalArgumentException malformed) {
      // Deliberately still record this as "seen" (lastSeenAttitudeTimestampSeconds isn't
      // updated above on this path) so a persistently malformed frame at a fixed bad timestamp
      // doesn't get silently retried as "new" forever -- matches malformedFrameCount's intent of
      // counting distinct bad frames, not the same one repeatedly.
      lastSeenAttitudeTimestampSeconds = timestampSeconds;
      recordMalformed(CanIds.ATTITUDE_API_ID, message, malformed);
    }
  }

  private static byte[] trim(CANReceiveMessage message) {
    return Arrays.copyOf(message.data, message.length);
  }

  /**
   * How many times an {@code acceptX} method has received a message for its own API ID that
   * failed to unpack as that frame's expected byte length. Should stay at 0 -- not a RioBridge
   * sender-side bug either way, since {@code CanFrames}' packing methods always emit exactly 8
   * bytes.
   */
  int malformedFrameCount() {
    return malformedFrameCount;
  }

  /**
   * Details of the most recent frame an {@code acceptX} method couldn't unpack, or {@code null}
   * if none has happened yet.
   */
  String lastMalformedFrameDescription() {
    return lastMalformedFrameDescription;
  }

  private void recordMalformed(int apiId, CANReceiveMessage message, IllegalArgumentException malformed) {
    malformedFrameCount++;
    lastMalformedFrameDescription =
        String.format(
            "apiId=0x%X dataLength=%d rawTimestamp=%d: %s",
            apiId, message.length, message.timestamp, malformed.getMessage());
  }

  StatusFrame latestStatus() {
    return latestStatus;
  }

  double latestStatusTimestampSeconds() {
    return latestStatusTimestampSeconds;
  }

  EncodersFrame latestEncoders() {
    return latestEncoders;
  }

  double latestEncodersTimestampSeconds() {
    return latestEncodersTimestampSeconds;
  }

  AttitudeSample latestAttitude() {
    return latestAttitude;
  }

  /**
   * Every distinct (by timestamp) Attitude sample accepted since the last call, oldest first, for
   * AdvantageKit's per-sample odometry arrays. Call once per {@code updateInputs} -- this drains
   * the buffer. See class javadoc: expect 0 or 1 elements per call in practice now, not several.
   */
  List<AttitudeSample> drainAttitudeSamples() {
    if (pendingAttitudeSamples.isEmpty()) {
      return Collections.emptyList();
    }
    List<AttitudeSample> drained = List.copyOf(pendingAttitudeSamples);
    pendingAttitudeSamples.clear();
    return drained;
  }
}
