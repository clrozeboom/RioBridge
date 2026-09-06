package frc.robot.subsystems.drive.riobridge;

import java.util.List;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.system.Timer;

/**
 * {@code GyroIO} for the RioBridge's navX. Matches the root README's Core-side integration
 * section: {@code connected} is driven by Attitude-frame staleness at a 100 ms threshold, and
 * per-frame timestamps map onto AdvantageKit's {@code odometryYawPositions[]}/{@code
 * sampleTimestamps[]} pairing.
 *
 * <p>Construct one {@link RioBridgeCan} per robot (it owns the one CAN stream session for all
 * three RioBridge frames) and share it with whatever reads the encoders; don't open a second
 * session here.
 */
public class GyroIORioBridge implements GyroIO {
  private static final double STALE_THRESHOLD_SECONDS = 0.100;

  private final RioBridgeCan bus;

  public GyroIORioBridge(RioBridgeCan bus) {
    this.bus = bus;
  }

  @Override
  public void updateInputs(GyroIOInputs inputs) {
    bus.poll();

    AttitudeSample latest = bus.latestAttitude();
    inputs.connected =
        latest != null
            && (Timer.getMonotonicTimestamp() - latest.timestampSeconds()) < STALE_THRESHOLD_SECONDS;
    if (latest != null) {
      inputs.yawPosition = Rotation2d.fromDegrees(latest.attitude().yawDeg());
      inputs.yawVelocityRadPerSec = Math.toRadians(latest.attitude().yawRateDegPerSec());
    }

    List<AttitudeSample> samples = bus.drainAttitudeSamples();
    double[] timestamps = new double[samples.size()];
    Rotation2d[] positions = new Rotation2d[samples.size()];
    for (int i = 0; i < samples.size(); i++) {
      AttitudeSample sample = samples.get(i);
      timestamps[i] = sample.timestampSeconds();
      positions[i] = Rotation2d.fromDegrees(sample.attitude().yawDeg());
    }
    inputs.odometryYawTimestamps = timestamps;
    inputs.odometryYawPositions = positions;
  }
}
