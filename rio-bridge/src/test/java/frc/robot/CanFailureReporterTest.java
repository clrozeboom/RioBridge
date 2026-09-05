package frc.robot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** No HAL/CAN involved -- see {@link CanFailureReporter}'s javadoc for why. */
class CanFailureReporterTest {
  @Test
  void firstFailurePrintsImmediately() {
    CanFailureReporter reporter = new CanFailureReporter();
    String line = reporter.onFailure(0.0, "boom");
    assertNotNull(line, "the first failure should print, not wait out the interval");
    assertTrue(line.contains("boom"), "the underlying exception message should be in the line");
  }

  @Test
  void repeatedFailuresWithinTheIntervalDoNotReprint() {
    CanFailureReporter reporter = new CanFailureReporter();
    reporter.onFailure(0.0, "boom");

    String line = reporter.onFailure(0.5, "boom");

    assertNull(line, "a second failure well inside the reprint interval should stay quiet");
  }

  @Test
  void failuresPastTheIntervalReprint() {
    CanFailureReporter reporter = new CanFailureReporter();
    reporter.onFailure(0.0, "boom");

    String line = reporter.onFailure(CanFailureReporter.REPRINT_INTERVAL_SECONDS + 0.01, "boom");

    assertNotNull(line, "a failure past the reprint interval should print again");
  }

  @Test
  void recoveryAfterFailingPrintsOnce() {
    CanFailureReporter reporter = new CanFailureReporter();
    reporter.onFailure(0.0, "boom");

    String recovered = reporter.onSuccess();
    String secondSuccess = reporter.onSuccess();

    assertNotNull(recovered, "the first success after a failure should print a recovery line");
    assertNull(secondSuccess, "a second, still-healthy success shouldn't print again");
  }

  @Test
  void successWithNoPriorFailureStaysQuiet() {
    CanFailureReporter reporter = new CanFailureReporter();

    assertNull(reporter.onSuccess(), "the common case -- healthy writes -- should print nothing");
  }
}
