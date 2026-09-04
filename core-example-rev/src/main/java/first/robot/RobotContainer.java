// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.robot;

import org.wpilib.command3.Command;
import org.wpilib.command3.button.CommandGamepad;
import org.wpilib.driverstation.Gamepad;

import first.robot.commands.Autos;
import first.robot.subsystems.TestSubsystem;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 *
 * <p>The CTRE example this was modeled on put an autonomous chooser on the dashboard via {@code
 * org.wpilib.smartdashboard.SendableChooser}/{@code SmartDashboard.putData}. Neither exists in
 * WPILib 2027 alpha-7 (confirmed absent from the real wpilibj-java sources -- the smartdashboard
 * package there only has Field2d/Mechanism2d now); wherever that functionality moved to is out of
 * scope for this smoke test, so autonomous just always runs {@link #simpleClockwiseAuto}.
 */
public class RobotContainer {
  // The robot's subsystems
  private final TestSubsystem testSubsystem = new TestSubsystem();

  // The autonomous routine
  private final Command simpleClockwiseAuto = Autos.simpleClockwiseAuto(testSubsystem);

  CommandGamepad controller = new CommandGamepad(0);

  /** The container for the robot. Contains subsystems, OI devices, and commands. */
  public RobotContainer() {
    // Configure the button bindings
    configureButtonBindings();

    // Configure default commands: motor idle unless a button is held
    testSubsystem.setDefaultCommand(
        testSubsystem
            .run(coro -> testSubsystem.stop())
            .withPriority(Command.LOWEST_PRIORITY)
            .named("Motor Idle"));
  }

  /**
   * Use this method to define your button->command mappings. Buttons can be created by
   * instantiating a {@link org.wpilib.driverstation.GenericHID} or one of its subclasses ({@link
   * org.wpilib.driverstation.Joystick} or {@link Gamepad}), and then passing it to a {@link
   * org.wpilib.command3.button.JoystickButton}.
   */
  private void configureButtonBindings() {
    controller
        .rightBumper()
        .onTrue(Command.noRequirements(coro -> testSubsystem.runClockwise()).named("Run Clockwise"))
        .onFalse(Command.noRequirements(coro -> testSubsystem.stopMotors()).named("Stop Motor"));
    controller
        .leftBumper()
        .onTrue(
            Command.noRequirements(coro -> testSubsystem.runCounterClockwise())
                .named("Run Counterclockwise"))
        .onFalse(Command.noRequirements(coro -> testSubsystem.stopMotors()).named("Stop Motor"));
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return simpleClockwiseAuto;
  }
}
