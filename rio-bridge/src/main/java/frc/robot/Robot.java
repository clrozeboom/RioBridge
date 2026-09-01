package frc.robot;

import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.CAN;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.protocol.CanFrames;
import frc.robot.protocol.CanIds;

/**
 * RioBridge: reads four analog absolute encoders and a navX2 under 2026 WPILib and republishes
 * them as CAN frames on a bus with nothing else on it (ADR-0002).
 *
 * <p>Read-only by design (ADR-0003) -- this class must never construct a motor controller or any
 * other output device. Every frame is sent explicitly from {@link #robotPeriodic}, never with
 * {@code writePacketRepeating} (ADR-0004), so a frame arriving at the Core is evidence this loop
 * actually ran and actually read the sensors.
 */
public class Robot extends TimedRobot {
  // The fast frames (Encoders, Attitude) want CanIds.ENCODERS_HZ/ATTITUDE_HZ (100 Hz, currently
  // equal); the main loop runs at that rate and the Status frame is decimated down to
  // CanIds.STATUS_HZ from it, rather than running two independent periodic timers.
  private static final double LOOP_PERIOD_SECONDS = 1.0 / CanIds.ENCODERS_HZ;
  private static final int STATUS_LOOP_DIVIDER = CanIds.ENCODERS_HZ / CanIds.STATUS_HZ;

  // TODO (README "To verify" #4): confirm all four absolute encoders are on onboard analog
  // channels 0-3 and none are on the MXP analog channels, then correct this if not.
  private static final int[] ENCODER_CHANNELS = {0, 1, 2, 3};

  private final CAN can = new CAN(CanIds.DEVICE_NUMBER);
  private final AnalogInput[] encoders = new AnalogInput[ENCODER_CHANNELS.length];
  private final AttitudeSource attitude;

  private int loopCounter = 0;

  public Robot() {
    this(new NavxAttitudeSource());
  }

  /** Package-visible so a fake {@link AttitudeSource} can exercise this class without navX. */
  Robot(AttitudeSource attitude) {
    super(LOOP_PERIOD_SECONDS);
    this.attitude = attitude;
    for (int i = 0; i < ENCODER_CHANNELS.length; i++) {
      encoders[i] = new AnalogInput(ENCODER_CHANNELS[i]);
    }
  }

  @Override
  public void close() {
    for (AnalogInput encoder : encoders) {
      encoder.close();
    }
    can.close();
    super.close();
  }

  @Override
  public void robotPeriodic() {
    loopCounter++;

    int[] rawCounts = new int[encoders.length];
    for (int i = 0; i < encoders.length; i++) {
      rawCounts[i] = encoders[i].getValue();
    }
    can.writePacket(CanFrames.packEncoders(rawCounts), CanIds.ENCODERS_API_ID);

    boolean navxConnected = attitude.isConnected();
    can.writePacket(
        CanFrames.packAttitude(
            navxConnected ? attitude.getYawDeg() : 0.0,
            navxConnected ? attitude.getYawRateDegPerSec() : 0.0,
            navxConnected ? attitude.getPitchDeg() : 0.0,
            navxConnected ? attitude.getRollDeg() : 0.0),
        CanIds.ATTITUDE_API_ID);

    if (loopCounter % STATUS_LOOP_DIVIDER == 0) {
      int flags = navxConnected ? CanFrames.FLAG_NAVX_CONNECTED : 0;
      int uptimeSeconds = (int) Timer.getFPGATimestamp();
      can.writePacket(
          CanFrames.packStatus(loopCounter, uptimeSeconds, flags, CanFrames.PROTOCOL_VERSION),
          CanIds.STATUS_API_ID);
    }
  }
}
