package first.robot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;

/**
 * Constructs {@link RobotContainer} -- and so {@link first.robot.subsystems.TestSubsystem}'s real
 * {@code SparkMax} -- under the desktop HAL sim. This is the actual answer to "does REVLib's
 * native driver link against WPILib alpha-7's HAL": {@code compileJava} only proves the Java
 * source compiles, not that the JNI native library resolves and loads.
 *
 * <p><b>Currently disabled, not deleted, because it found a real bug -- not one in this project.</b>
 * {@code com.revrobotics.frc:REVLib-driver:2027.0.0-alpha-6}'s desktop (linux-x86_64) native
 * library, {@code libREVLibDriver.so}, has an unresolved {@code DT_NEEDED} entry for {@code
 * libBackendDriver.so} (confirmed with {@code readelf -d}), and no artifact by that name (or any
 * plausible variant: BackendDriver, REVBackendDriver, backend-driver, under
 * com/revrobotics/{frc,usb,driver}) exists anywhere on {@code maven.revrobotics.com}. This reads
 * as a packaging gap in that specific REVLib release, not a wiring mistake here -- everything up
 * to the native library actually loading works: the vendordep resolves, the jar compiles against
 * the real alpha-7 API, and {@code libREVLibDriver.so} itself is found and located correctly by
 * WPILib's runtime loader. Re-enable this once REV publishes a REVLib-driver build (alpha-6 or
 * later) whose native library doesn't have this dangling dependency.
 */
class RobotContainerTest {
  @BeforeEach
  void setup() {
    assertDoesNotThrow(() -> HAL.initialize());
  }

  @AfterEach
  void teardown() {
    HAL.shutdown();
  }

  @Test
  @Disabled(
      "com.revrobotics.frc:REVLib-driver:2027.0.0-alpha-6's libREVLibDriver.so needs "
          + "libBackendDriver.so, which isn't published anywhere on maven.revrobotics.com -- see "
          + "class javadoc")
  void constructsWithARealSparkMaxUnderHalSim() {
    RobotContainer container = assertDoesNotThrow(RobotContainer::new);
    assertDoesNotThrow(container::getAutonomousCommand);
  }
}
