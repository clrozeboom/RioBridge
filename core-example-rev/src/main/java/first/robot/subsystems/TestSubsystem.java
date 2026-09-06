// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.TestSubsystemConstants;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;

/** REVLib equivalent of the CTRE example's {@code TestSubsystem} -- one SPARK MAX, spun open-loop
 *  in either direction. Exists to prove REVLib + WPILib 2027 alpha-7 + commandsv2-java actually
 *  build and run together on this toolchain (see ../../../../../../../README.md), not to be a
 *  real drivetrain. */
public class TestSubsystem extends SubsystemBase {
  private final SparkMax motor =
      new SparkMax(
          TestSubsystemConstants.MOTOR_BUS.value,
          TestSubsystemConstants.MOTOR_DEVICE_ID,
          MotorType.kBrushless);

  public TestSubsystem() {
    configureMotor();
  }

  private void configureMotor() {
    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(IdleMode.kCoast);
    config.smartCurrentLimit(TestSubsystemConstants.SMART_CURRENT_LIMIT_AMPS);
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kNoPersistParameters);
  }

  public Command runClockwise() {
    return Commands.run(() -> motor.setThrottle(TestSubsystemConstants.CLOCKWISE_OUTPUT), this);
  }

  public Command runCounterClockwise() {
    return Commands.run(
        () -> motor.setThrottle(TestSubsystemConstants.COUNTERCLOCKWISE_OUTPUT), this);
  }

  public Command stopMotors() {
    return Commands.run(this::stop, this);
  }

  public void stop() {
    motor.stopMotor();
  }
}
