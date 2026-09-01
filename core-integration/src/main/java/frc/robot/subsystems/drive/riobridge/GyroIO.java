package frc.robot.subsystems.drive.riobridge;

import org.wpilib.math.geometry.Rotation2d;

/**
 * The drive subsystem's gyro seam, in the shape AdvantageKit swerve templates (including the
 * {@code spark_swerve} template this repo's README targets) already expect: a mutable inputs
 * struct an {@code @AutoLog}-annotated real project would log, plus the per-sample odometry
 * arrays AdvantageKit's odometry thread consumes alongside each module's position samples.
 *
 * <p>If your project already has a {@code GyroIO} (it does, if it started from an AdvantageKit
 * template), don't add this one -- point {@link GyroIORioBridge} at your existing interface
 * instead. This copy exists so the package compiles standalone; see core-integration/README.md.
 */
public interface GyroIO {
  /** Mutable inputs struct; a real project would put {@code @AutoLog} on this. */
  class GyroIOInputs {
    public boolean connected = false;
    public Rotation2d yawPosition = Rotation2d.ZERO;
    public double yawVelocityRadPerSec = 0.0;
    public double[] odometryYawTimestamps = new double[0];
    public Rotation2d[] odometryYawPositions = new Rotation2d[0];
  }

  default void updateInputs(GyroIOInputs inputs) {}
}
