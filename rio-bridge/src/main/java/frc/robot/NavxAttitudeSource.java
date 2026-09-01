package frc.robot;

import com.studica.frc.AHRS;
import com.studica.frc.AHRS.NavXComType;

/**
 * Wraps the navX2 on the MXP port. This is 2026 WPILib's unmodified navX vendor library (ADR-0001)
 * -- RioBridge only reads it and republishes over CAN, never patches it.
 *
 * <p>Verified against the real {@code com.studica.frc:Studica-java:2026.0.0} sources (once the
 * vendordep landed in {@code vendordeps/Studica.json}): {@code NavXComType.kMXP_SPI} is exactly
 * the constant for "navX on the MXP SPI bus", and {@code getRate()}'s javadoc confirms yaw rate
 * in degrees/second. {@code getYaw()}/{@code getPitch()}/{@code getRoll()} return {@code float}
 * on the real API (widened to {@code double} here, not narrowed -- no precision lost). This class
 * also runs under the desktop HAL sim in {@code RobotRealNavxTest}, which constructs a real
 * {@code AHRS} on port {@code kMXP_SPI} and confirms it connects.
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
