package frc.robot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import edu.wpi.first.hal.HAL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the public {@code Robot()} constructor -- the real {@link NavxAttitudeSource}, i.e.
 * a real {@code AHRS}, not {@link RobotTest}'s fake -- under the desktop HAL simulation. Kept
 * separate from {@link RobotTest} so this one real-navX instance doesn't share a HAL session with
 * the fake-backed ones.
 */
class RobotRealNavxTest {
  private Robot robot;

  @BeforeEach
  void setup() {
    assertDoesNotThrow(() -> HAL.initialize(500, 0));
  }

  @AfterEach
  void teardown() {
    if (robot != null) {
      robot.close();
    }
    HAL.shutdown();
  }

  @Test
  void constructsAndRunsWithTheRealNavxAttitudeSource() {
    robot = assertDoesNotThrow(() -> new Robot());
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(robot::robotPeriodic);
    }
  }
}
