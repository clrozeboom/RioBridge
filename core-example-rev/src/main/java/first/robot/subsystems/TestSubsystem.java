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
import org.wpilib.command3.*;

/** REVLib equivalent of the CTRE example's {@code TestSubsystem} -- one SPARK MAX, spun open-loop
 *  in either direction. Exists to prove REVLib + WPILib 2027 alpha-7 + commandsv3-java actually
 *  build and run together on this toolchain (see ../../../../../../../README.md), not to be a
 *  real drivetrain. */
// Mechanism is an interface at alpha-7 (it was a class at whatever alpha the CTRE example this
// was modeled on was written against -- confirmed by decompiling Mechanism.class here).
public class TestSubsystem implements Mechanism {
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
    // implicitly require `this`
    return this.run(coro -> motor.setThrottle(TestSubsystemConstants.CLOCKWISE_OUTPUT))
        .named("MotorSpinClockwise");
  }

  public Command runCounterClockwise() {
    // implicitly require `this`
    return this.run(coro -> motor.setThrottle(TestSubsystemConstants.COUNTERCLOCKWISE_OUTPUT))
        .named("MotorSpinCounterClockwise");
  }

  public Command stopMotors() {
    return this.run(coro -> stop()).named("StopMotor");
  }

  public void stop() {
    motor.stopMotor();
  }
}
