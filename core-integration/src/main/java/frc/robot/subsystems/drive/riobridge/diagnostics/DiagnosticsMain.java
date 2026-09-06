package frc.robot.subsystems.drive.riobridge.diagnostics;

import org.wpilib.framework.RobotBase;

/**
 * Entry point for {@link DiagnosticsRobot}. See docs/hardware-verification.md: copy this
 * package's classes into your real Core project and point its actual {@code Main} at {@code
 * DiagnosticsRobot} instead of adding a second {@code main()} -- this class exists so the
 * diagnostics package has one ready to copy verbatim if that's easier for your project layout.
 *
 * <p>{@code RobotBase.startRobot}'s overload is one of the real API differences between alpha-6
 * (this project's current pin: only {@code startRobot(Class<T>)} exists, hence {@code
 * DiagnosticsRobot.class} below) and alpha-7 (only the {@code Supplier<T>} overload exists,
 * hence {@code DiagnosticsRobot::new}) -- see core-example-rev/README.md's API-differences table.
 * Match whichever your real Core project is actually pinned to.
 */
public final class DiagnosticsMain {
  private DiagnosticsMain() {}

  public static void main(String[] args) {
    RobotBase.startRobot(DiagnosticsRobot.class);
  }
}
