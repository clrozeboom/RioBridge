package frc.robot.diagnostics;

import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.TimedRobot;

/**
 * Root README "to verify" item 4: are all four absolute encoders really on onboard analog
 * channels 0-3, with none on MXP analog? Prints all eight roboRIO analog channels (onboard 0-3,
 * MXP 4-7) so you can rotate each encoder by hand, watch which channel number moves, and confirm
 * -- rather than trust -- the {@code ENCODER_CHANNELS} assumption in {@code Robot.java}.
 *
 * <p>Throwaway diagnostic, not part of the normal RioBridge program -- see
 * docs/hardware-verification.md for how to deploy this in place of {@code Robot} for one bench
 * session, then revert.
 */
public class EncoderChannelDiagnostic extends TimedRobot {
  private static final int CHANNEL_COUNT = 8;
  private static final double PRINT_INTERVAL_SECONDS = 0.5;

  private final AnalogInput[] channels = new AnalogInput[CHANNEL_COUNT];
  private int loopCounter = 0;

  public EncoderChannelDiagnostic() {
    super(PRINT_INTERVAL_SECONDS);
    for (int i = 0; i < CHANNEL_COUNT; i++) {
      channels[i] = new AnalogInput(i);
    }
    System.out.println("=== EncoderChannelDiagnostic ===");
    System.out.println(
        "Rotate one encoder at a time and watch which channel's raw value changes. Onboard =");
    System.out.println("channels 0-3; MXP = channels 4-7. A floating/unwired channel reads noisy");
    System.out.println("near 0 or near 4095, not a smooth, stable value.");
  }

  @Override
  public void close() {
    for (AnalogInput channel : channels) {
      channel.close();
    }
    super.close();
  }

  @Override
  public void robotPeriodic() {
    loopCounter++;
    StringBuilder line = new StringBuilder("[" + loopCounter + "] ");
    for (int i = 0; i < CHANNEL_COUNT; i++) {
      String label = i < 4 ? "onboard[" + i + "]" : "MXP[" + i + "]";
      line.append(label).append('=').append(channels[i].getValue());
      if (i != CHANNEL_COUNT - 1) {
        line.append("  ");
      }
    }
    System.out.println(line);
  }
}
