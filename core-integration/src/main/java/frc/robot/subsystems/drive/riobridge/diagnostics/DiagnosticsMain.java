package frc.robot.subsystems.drive.riobridge.diagnostics;

import org.wpilib.framework.RobotBase;

/**
 * Entry point for {@link DiagnosticsRobot}. See docs/hardware-verification.md: copy this
 * package's classes into your real Core project and point its actual {@code Main} at {@code
 * DiagnosticsRobot::new} instead of adding a second {@code main()} -- this class exists so the
 * diagnostics package has one ready to copy verbatim if that's easier for your project layout.
 */
public final class DiagnosticsMain {
  private DiagnosticsMain() {}

  public static void main(String[] args) {
    RobotBase.startRobot(DiagnosticsRobot::new);
  }
}
