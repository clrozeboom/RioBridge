package frc.robot.subsystems.drive.riobridge.diagnostics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link TimestampUnitsCheck#classify} directly -- the actual "which unit is this"
 * decision, with no CAN session involved -- against synthetic secondsPerUnit ratios standing in
 * for what real milliseconds/microseconds/nanoseconds timestamps would produce.
 */
class TimestampUnitsCheckTest {
  @Test
  void classifiesMillisecondsFromARealisticRatio() {
    // 40 Status frames at 20 Hz span ~1.95s (39 gaps of 50ms); if raw units are ms, rawDelta
    // would be ~1950.
    String verdict = TimestampUnitsCheck.classify(1.95 / 1950, 40, 1950, 1.95);
    assertTrue(verdict.startsWith("MILLISECONDS"), verdict);
  }

  @Test
  void classifiesNanosecondsFromARealisticRatio() {
    long rawDelta = 1_950_000_000L;
    String verdict = TimestampUnitsCheck.classify(1.95 / rawDelta, 40, rawDelta, 1.95);
    assertTrue(verdict.startsWith("NANOSECONDS"), verdict);
  }

  @Test
  void classifiesMicrosecondsFromARealisticRatio() {
    long rawDelta = 1_950_000L;
    String verdict = TimestampUnitsCheck.classify(1.95 / rawDelta, 40, rawDelta, 1.95);
    assertTrue(verdict.startsWith("MICROSECONDS"), verdict);
  }

  @Test
  void flagsAnythingElseAsUnrecognizedRatherThanGuessing() {
    // secondsPerUnit of 1.0 would mean the raw field increments once per second -- not any of
    // the three units either javadoc claimed.
    String verdict = TimestampUnitsCheck.classify(1.0, 40, 2, 1.95);
    assertTrue(verdict.startsWith("UNRECOGNIZED"), verdict);
  }
}
