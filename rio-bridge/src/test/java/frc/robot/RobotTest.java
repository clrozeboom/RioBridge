package frc.robot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import edu.wpi.first.hal.HAL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Robot} against the desktop HAL simulation, so the CAN/AnalogInput wiring is
 * checked even though nothing here asserts on the frames actually sent -- there is no simulated
 * CAN bus to read them back from. {@link frc.robot.protocol.CanFramesTest} is what checks wire
 * content.
 */
class RobotTest {
  private FakeAttitudeSource attitude;
  private Robot robot;

  @BeforeEach
  void setup() {
    assertDoesNotThrow(() -> HAL.initialize(500, 0));
    attitude = new FakeAttitudeSource();
    robot = new Robot(attitude);
  }

  @AfterEach
  void teardown() {
    // AnalogInput/CAN channel allocation is tracked process-wide by the HAL, and survives
    // HAL.shutdown() within the same JVM -- close() must run first or the next test's Robot()
    // throws AllocationException on channel reuse.
    robot.close();
    HAL.shutdown();
  }

  @Test
  void robotPeriodicRunsRepeatedlyWithNavxConnected() {
    attitude.connected = true;
    attitude.yawDeg = 12.5;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(robot::robotPeriodic);
    }
  }

  @Test
  void robotPeriodicRunsWhenNavxIsDisconnected() {
    attitude.connected = false;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(robot::robotPeriodic);
    }
  }

  private static class FakeAttitudeSource implements AttitudeSource {
    boolean connected = true;
    double yawDeg;
    double yawRateDegPerSec;
    double pitchDeg;
    double rollDeg;

    @Override
    public boolean isConnected() {
      return connected;
    }

    @Override
    public double getYawDeg() {
      return yawDeg;
    }

    @Override
    public double getYawRateDegPerSec() {
      return yawRateDegPerSec;
    }

    @Override
    public double getPitchDeg() {
      return pitchDeg;
    }

    @Override
    public double getRollDeg() {
      return rollDeg;
    }
  }
}
