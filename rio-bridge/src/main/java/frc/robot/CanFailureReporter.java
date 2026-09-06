package frc.robot;

/**
 * Throttled console reporting for {@code CAN.writePacket} failures.
 *
 * <p>{@link Robot} used to let an {@code UncleanStatusException} crash the whole program -- which
 * happens on the very first tick whenever nothing is ACKing frames on the bus yet (Core not
 * powered, cable not connected, or its CAN interface not brought up), and gives an operator
 * nothing to go on beyond the Comm light cycling as the program restarts. This prints a
 * diagnosable line to the Driver Station console on the first failure, at most once every {@link
 * #REPRINT_INTERVAL_SECONDS} while it continues (not once per 10 ms tick), and once more on
 * recovery -- instead of either crashing or retrying silently forever.
 *
 * <p>Pure state machine, no HAL calls -- takes the current timestamp and exception message as
 * arguments rather than reading them itself, so it's testable without a HAL/CAN session. See
 * {@code CanFailureReporterTest}.
 */
final class CanFailureReporter {
  static final double REPRINT_INTERVAL_SECONDS = 1.0;

  private boolean failing = false;
  private double nextPrintAtSeconds = 0;

  /**
   * Call when a {@code writePacket} call throws.
   *
   * @return the line to print to the console, or {@code null} if nothing should print this tick
   *     (still within {@link #REPRINT_INTERVAL_SECONDS} of the last print).
   */
  String onFailure(double nowSeconds, String exceptionMessage) {
    if (!failing) {
      failing = true;
      nextPrintAtSeconds = nowSeconds;
    }
    if (nowSeconds < nextPrintAtSeconds) {
      return null;
    }
    nextPrintAtSeconds = nowSeconds + REPRINT_INTERVAL_SECONDS;
    return "RioBridge: CAN write failed ("
        + exceptionMessage
        + "). This almost always means nothing is ACKing frames on this bus yet -- check the"
        + " Core is powered on, its CAN_S1 interface is actually up, and the cable between them"
        + " is connected. Will print again every "
        + (int) REPRINT_INTERVAL_SECONDS
        + "s while this continues.";
  }

  /**
   * Call after a {@code writePacket} call succeeds.
   *
   * @return a one-time recovery line if this follows one or more failures, or {@code null} if
   *     writes weren't failing (the common case -- most ticks print nothing at all).
   */
  String onSuccess() {
    if (!failing) {
      return null;
    }
    failing = false;
    return "RioBridge: CAN writes recovered.";
  }
}
