package frc.robot.subsystems.drive.riobridge.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import frc.robot.subsystems.drive.riobridge.diagnostics.BusHealthMonitor.BusReading;
import org.junit.jupiter.api.Test;

/** Exercises {@link BusHealthMonitor#regressed} directly, with hand-built readings. */
class BusHealthMonitorTest {
  private static BusReading reading(int busOff, int txFull, int rxErr, int txErr) {
    return new BusReading("CAN_S1", 0.0, busOff, txFull, rxErr, txErr);
  }

  @Test
  void identicalReadingsDoNotRegress() {
    BusReading a = reading(0, 0, 0, 0);
    BusReading b = reading(0, 0, 0, 0);
    assertFalse(BusHealthMonitor.regressed(a, b));
  }

  @Test
  void anIncreaseInAnyCounterIsARegression() {
    BusReading baseline = reading(1, 2, 3, 4);
    assertTrue(BusHealthMonitor.regressed(baseline, reading(2, 2, 3, 4)), "busOffCount");
    assertTrue(BusHealthMonitor.regressed(baseline, reading(1, 3, 3, 4)), "txFullCount");
    assertTrue(BusHealthMonitor.regressed(baseline, reading(1, 2, 4, 4)), "receiveErrorCount");
    assertTrue(BusHealthMonitor.regressed(baseline, reading(1, 2, 3, 5)), "transmitErrorCount");
  }

  @Test
  void countersAreEventCountsAndNeverExpectedToDecrease() {
    // Not a real scenario (these are cumulative since boot) but confirms regressed() only
    // triggers on an increase, not any change.
    BusReading baseline = reading(5, 5, 5, 5);
    assertFalse(BusHealthMonitor.regressed(baseline, reading(5, 5, 5, 5)));
  }
}
