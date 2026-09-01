package frc.robot;

import com.studica.frc.AHRS;
import com.studica.frc.AHRS.NavXComType;

/**
 * Wraps the navX2 on the MXP port. This is 2026 WPILib's unmodified navX vendor library (ADR-0001)
 * -- RioBridge only reads it and republishes over CAN, never patches it.
 *
 * <p><b>Not build-verified.</b> Every other file under {@code rio-bridge/} was compiled and
 * tested against the real 2026.2.2 WPILib jars while writing this project; this one file could
 * not be, because the navX vendordep JSON lives on Studica's own Maven host and its exact URL
 * for the 2026 season wasn't reachable while writing this code (see rio-bridge/README.md). Add
 * the real vendordep through the WPILib VS Code extension's "Manage Vendor Libraries" -&gt;
 * "Install new library (online)" before the first build, then confirm the two things below
 * against its javadoc:
 *
 * <ul>
 *   <li>{@code NavXComType.kMXP_SPI} is the enum constant for "navX on the MXP SPI bus" -- the
 *       navX2's connection here.
 *   <li>{@code getRate()} returns yaw rate in degrees/second (true for every navX generation to
 *       date, since it implements WPILib's {@code Gyro} interface).
 * </ul>
 */
public class NavxAttitudeSource implements AttitudeSource {
  private final AHRS ahrs = new AHRS(NavXComType.kMXP_SPI);

  @Override
  public boolean isConnected() {
    return ahrs.isConnected();
  }

  @Override
  public double getYawDeg() {
    return ahrs.getYaw();
  }

  @Override
  public double getYawRateDegPerSec() {
    return ahrs.getRate();
  }

  @Override
  public double getPitchDeg() {
    return ahrs.getPitch();
  }

  @Override
  public double getRollDeg() {
    return ahrs.getRoll();
  }
}
