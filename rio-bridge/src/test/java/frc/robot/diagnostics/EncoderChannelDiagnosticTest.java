package frc.robot.diagnostics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import edu.wpi.first.hal.HAL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises {@link EncoderChannelDiagnostic} under the desktop HAL sim, all 8 channels. */
class EncoderChannelDiagnosticTest {
  private EncoderChannelDiagnostic diagnostic;

  @BeforeEach
  void setup() {
    assertDoesNotThrow(() -> HAL.initialize(500, 0));
  }

  @AfterEach
  void teardown() {
    if (diagnostic != null) {
      diagnostic.close();
    }
    HAL.shutdown();
  }

  @Test
  void constructsAllEightChannelsAndRunsRepeatedly() {
    diagnostic = assertDoesNotThrow(EncoderChannelDiagnostic::new);
    for (int i = 0; i < 5; i++) {
      assertDoesNotThrow(diagnostic::robotPeriodic);
    }
  }
}
