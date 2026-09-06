package frc.robot.subsystems.drive.riobridge;

import frc.robot.protocol.CanFrames.EncodersFrame;
import frc.robot.protocol.CanFrames.StatusFrame;
import frc.robot.protocol.CanIds;
import java.util.List;
import org.wpilib.hardware.bus.CAN;
import org.wpilib.hardware.hal.can.CANReceiveMessage;

/**
 * Owns the RioBridge's CAN reads: one {@link CAN} device handle for the RioBridge's own device
 * identity (bus + device ID + manufacturer + device type), read per frame type via {@code
 * readPacketLatest} -- not the buffered stream session the original (alpha-7-targeted) design
 * used. Construct one per robot and share it with whatever reads the encoders (see {@link
 * #latestEncoders()}) as well as {@link GyroIORioBridge} -- don't open a second handle.
 *
 * <p><b>Why this exists: the stream session API never delivered payload data on real hardware.</b>
 * The previous version of this class used {@code CANJNI.openCANStreamSession}/{@code
 * readCANStreamSession} with {@code CANStreamMessage} (see git history). That populated {@code
 * CANStreamMessage.timestamp}/{@code .messageId} correctly but {@code .length} was 0 on
 * essentially every real frame -- confirmed on real hardware at a rate matching the protocol's
 * entire combined frame rate (~220/sec), not an occasional glitch: a malformed-frame counter
 * climbed in lockstep with the expected frame rate while the Attitude frame count stayed at 0. See
 * {@code docs/wpilib-bug-report-can-stream-payload.md} for the full drafted bug report, including
 * a separate, genuine JVM segfault the stream session's overflow path hit along the way
 * (`CANStreamOverflowException` null-derefs while being constructed, before any Java `catch`
 * could run) -- a second reason to prefer this design even setting the payload bug aside.
 *
 * <p>This class is the fix: the older, non-streaming, per-device {@code CAN}/{@code CANAPIJNI}
 * API instead of the buffered stream session -- a structurally different native code path
 * (device-scoped {@code readCANPacketLatest} rather than a shared, mask-filtered stream buffer).
 * <b>Confirmed working on real hardware:</b> this exact design, hand-ported to a real
 * alpha-6-pinned robot project
 * ([clrozeboom/NerdSwerveYAGSL2026](https://github.com/clrozeboom/NerdSwerveYAGSL2026)), was
 * deployed to a real SystemCore and drove a real swerve robot's gyro and encoders -- the
 * malformed-frame counter stayed at 0 and Attitude samples came back nonzero (~50/sec, not
 * ~100/sec -- expected, not a regression; see below), across autonomous and teleop runs. So the
 * payload-marshaling gap really was specific to the stream session API -- this per-device API
 * marshals payload bytes correctly on the same platform, same WPILib version, same hardware.
 *
 * <p><b>What this loses versus the stream session design: true per-sample buffering.</b> {@code
 * readPacketLatest} only ever returns the single most recent packet for a given API ID -- there's
 * no history of every sample received between two polls, unlike a buffered stream session sized
 * generously enough to hold them all. {@link #drainAttitudeSamples()} keeps the same {@code List}
 * shape the old stream-session design returned (so {@link GyroIORioBridge} needed no changes), but
 * expect it to hold 0 or 1 elements per call now, not several: a caller polling a 100 Hz Attitude
 * frame at {@code TimedRobot}'s 50 Hz default period structurally cannot observe more than 50
 * distinct samples/sec no matter how well everything else is working, confirmed on real hardware
 * landing right at that ceiling. That is a real loss of resolution against the design's original
 * intent -- see {@code RioBridgeCanDemux}'s class javadoc for exactly how the dedup that produces
 * it works.
 *
 * <p>{@link CANReceiveMessage#timestamp}'s javadoc is unambiguous -- "Timestamp message was
 * received, in microseconds (wpi time)" -- unlike {@code CANStreamMessage}'s self-contradicting
 * one, and matches the microsecond measurement {@code TimestampUnitsCheck} already confirmed
 * against the stream API, so {@link RioBridgeCanDemux}'s timestamp scaling carries over unchanged.
 */
public class RioBridgeCan implements AutoCloseable {
  private final CAN can;
  private final CANReceiveMessage statusMessage = new CANReceiveMessage();
  private final CANReceiveMessage encodersMessage = new CANReceiveMessage();
  private final CANReceiveMessage attitudeMessage = new CANReceiveMessage();
  private final RioBridgeCanDemux demux = new RioBridgeCanDemux();

  /**
   * @param bus a raw HAL bus id, e.g. {@code org.wpilib.hardware.hal.CANBusMap.CAN_S1} -- not a
   *     {@code CANPort}, which doesn't exist at this project's now-current alpha-6 WPILib pin (see
   *     core-example-rev/README.md's "Using WPILib alpha-6 instead" for why that's fine: {@code
   *     CANBusMap} has the exact same values as plain {@code int} constants).
   */
  public RioBridgeCan(int bus) {
    can = new CAN(bus, CanIds.DEVICE_NUMBER, CAN.TEAM_MANUFACTURER, CAN.TEAM_DEVICE_TYPE);
  }

  @Override
  public void close() {
    can.close();
  }

  /** Reads the latest cached packet for each of the RioBridge protocol's three frames. */
  public void poll() {
    if (can.readPacketLatest(CanIds.STATUS_API_ID, statusMessage)) {
      demux.acceptStatus(statusMessage);
    }
    if (can.readPacketLatest(CanIds.ENCODERS_API_ID, encodersMessage)) {
      demux.acceptEncoders(encodersMessage);
    }
    if (can.readPacketLatest(CanIds.ATTITUDE_API_ID, attitudeMessage)) {
      demux.acceptAttitude(attitudeMessage);
    }
  }

  /**
   * Always 0. This implementation has no buffered session -- {@code readPacketLatest} just
   * overwrites a single cached packet per API ID, so there's nothing to overflow. Kept so existing
   * callers (e.g. diagnostics printing) built against the stream-session version of this class
   * don't need an unrelated code path removed.
   */
  public int overflowCount() {
    return 0;
  }

  /**
   * How many messages {@link #poll()} has received that didn't unpack as their expected frame --
   * see {@link RioBridgeCanDemux}'s class javadoc. Should stay at 0.
   */
  public int malformedFrameCount() {
    return demux.malformedFrameCount();
  }

  /** See {@link RioBridgeCanDemux#lastMalformedFrameDescription()}. */
  public String lastMalformedFrameDescription() {
    return demux.lastMalformedFrameDescription();
  }

  public StatusFrame latestStatus() {
    return demux.latestStatus();
  }

  public double latestStatusTimestampSeconds() {
    return demux.latestStatusTimestampSeconds();
  }

  public EncodersFrame latestEncoders() {
    return demux.latestEncoders();
  }

  public double latestEncodersTimestampSeconds() {
    return demux.latestEncodersTimestampSeconds();
  }

  public AttitudeSample latestAttitude() {
    return demux.latestAttitude();
  }

  /**
   * Every distinct Attitude sample received since the last call, oldest first, for AdvantageKit's
   * per-sample odometry arrays. Call once per {@code updateInputs} -- this drains the buffer. See
   * class javadoc: expect 0 or 1 elements per call now, not several -- a real, accepted loss of
   * resolution against the previous stream-session design's true multi-sample buffering.
   */
  public List<AttitudeSample> drainAttitudeSamples() {
    return demux.drainAttitudeSamples();
  }
}
