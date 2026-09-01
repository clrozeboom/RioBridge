package frc.robot.subsystems.drive.riobridge;

import frc.robot.protocol.CanFrames.EncodersFrame;
import frc.robot.protocol.CanFrames.StatusFrame;
import frc.robot.protocol.CanIds;
import java.util.List;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.hal.can.CANJNI;
import org.wpilib.hardware.hal.can.CANStreamMessage;
import org.wpilib.hardware.hal.can.CANStreamOverflowException;

/**
 * Owns the RioBridge's single CAN stream session (README.md: "The Core reads every frame with a
 * single buffered, timestamped stream session"). Construct one per robot and share it with
 * whatever reads the encoders (see {@link #latestEncoders()}) as well as {@link
 * GyroIORioBridge} -- don't open a second session.
 *
 * <p>The actual demux/decode logic lives in {@link RioBridgeCanDemux}, which has no JNI in it and
 * is unit tested directly. This class is the thin part that couldn't be build-verified in this
 * sandbox: there's no simulated CAN bus to open a stream session against, so neither the
 * constructor nor {@link #poll()} has run against a real HAL. See core-integration/README.md.
 */
public class RioBridgeCan implements AutoCloseable {
  private final int sessionHandle;
  private final CANStreamMessage[] scratch;
  private final RioBridgeCanDemux demux = new RioBridgeCanDemux();

  public RioBridgeCan(CANPort bus, int maxMessagesPerPoll) {
    sessionHandle =
        CANJNI.openCANStreamSession(
            bus.value, CanIds.STATUS_ARBITRATION_ID, CanIds.STREAM_MASK, maxMessagesPerPoll);
    scratch = new CANStreamMessage[maxMessagesPerPoll];
    for (int i = 0; i < scratch.length; i++) {
      scratch[i] = new CANStreamMessage();
    }
  }

  @Override
  public void close() {
    CANJNI.closeCANStreamSession(sessionHandle);
  }

  /** Reads and demultiplexes every frame the session has buffered since the last call. */
  public void poll() {
    CANStreamMessage[] received = scratch;
    int messagesRead;
    try {
      messagesRead = CANJNI.readCANStreamSession(sessionHandle, scratch, scratch.length);
    } catch (CANStreamOverflowException overflow) {
      // The session's buffer filled between polls, so some frames were dropped -- exactly the
      // dropped-frame case ADR-0004 already designs for (it just shows up as a bigger-than-usual
      // gap between two attitude timestamps). Demux what we did get rather than discarding it.
      received = overflow.getMessages();
      messagesRead = overflow.getMessagesRead();
    }
    for (int i = 0; i < messagesRead; i++) {
      demux.accept(received[i]);
    }
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
   * Every Attitude sample received since the last call, oldest first, for AdvantageKit's
   * per-sample odometry arrays. Call once per {@code updateInputs} -- this drains the buffer.
   */
  public List<AttitudeSample> drainAttitudeSamples() {
    return demux.drainAttitudeSamples();
  }
}
