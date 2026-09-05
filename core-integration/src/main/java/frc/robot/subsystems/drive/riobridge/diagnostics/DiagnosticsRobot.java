package frc.robot.subsystems.drive.riobridge.diagnostics;

import frc.robot.subsystems.drive.riobridge.RioBridgeCan;
import org.wpilib.framework.TimedRobot;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.system.Timer;

/**
 * A throwaway robot program for the two Core-side items in the root README's "to verify" list
 * that need a live CAN bus: timestamp units (item 1) and RX headroom under load (item 3). Not
 * part of the real Core project -- see docs/hardware-verification.md for how to deploy this in
 * place of your actual robot code for one bench session, then revert.
 *
 * <p>Runs {@link TimestampUnitsCheck} once at startup (there's no unconditional {@code
 * robotInit()} hook in the 2027 alpha's {@code IterativeRobotBase} the way there was in 2026 --
 * the constructor is the equivalent one-shot point), then continuously polls a {@link
 * RioBridgeCan} and both buses' {@link BusHealthMonitor} counters, printing a line per bus per
 * second and flagging any regression.
 */
public class DiagnosticsRobot extends TimedRobot {
  private static final CANPort RIOBRIDGE_BUS = CANPort.CAN_S1;
  private static final CANPort DRIVETRAIN_BUS = CANPort.CAN_S0;
  private static final double PRINT_INTERVAL_SECONDS = 1.0;
  private static final double TIMESTAMP_CHECK_TIMEOUT_SECONDS = 10.0;

  private final RioBridgeCan rioBridgeCan = new RioBridgeCan(RIOBRIDGE_BUS, 32);

  private BusHealthMonitor.BusReading previousDrivetrainReading;
  private BusHealthMonitor.BusReading previousRioBridgeReading;
  private double nextPrintAt = 0;
  private int attitudeFramesSinceLastPrint = 0;

  public DiagnosticsRobot() {
    System.out.println(
        "=== TimestampUnitsCheck: collecting Status frames on " + RIOBRIDGE_BUS + " ===");
    TimestampUnitsCheck.Result result =
        TimestampUnitsCheck.run(RIOBRIDGE_BUS, TIMESTAMP_CHECK_TIMEOUT_SECONDS);
    System.out.printf(
        "  wall-clock elapsed=%.3fs, raw timestamp delta=%d, secondsPerUnit=%g%n",
        result.wallClockDeltaSeconds(), result.rawTimestampDelta(), result.secondsPerUnit());
    System.out.println("  " + result.verdict());
    if (!result.succeeded()) {
      System.out.println(
          "  Bus health monitoring below will still run, but confirm the RioBridge is actually"
              + " transmitting before trusting the numbers.");
    }
    System.out.println("=== Starting continuous bus health monitoring (item 3) ===");
    System.out.println(
        "Command the drivetrain (or otherwise load bus 0) during this run -- an idle bus 0"
            + " doesn't exercise the shared SPI master this is checking.");

    previousDrivetrainReading = sampleSafely(DRIVETRAIN_BUS);
    previousRioBridgeReading = sampleSafely(RIOBRIDGE_BUS);
  }

  @Override
  public void close() {
    rioBridgeCan.close();
    super.close();
  }

  @Override
  public void robotPeriodic() {
    rioBridgeCan.poll();
    attitudeFramesSinceLastPrint += rioBridgeCan.drainAttitudeSamples().size();

    double now = Timer.getMonotonicTimestamp();
    if (now < nextPrintAt) {
      return;
    }
    nextPrintAt = now + PRINT_INTERVAL_SECONDS;

    BusHealthMonitor.BusReading drivetrain = sampleSafely(DRIVETRAIN_BUS);
    BusHealthMonitor.BusReading rioBridge = sampleSafely(RIOBRIDGE_BUS);

    printReading(drivetrain, previousDrivetrainReading);
    printReading(rioBridge, previousRioBridgeReading);
    System.out.printf(
        "  RioBridgeCan: attitudeFramesLastSecond=%d (expect ~%d at 100 Hz) overflowCount=%d%s%n",
        attitudeFramesSinceLastPrint,
        100,
        rioBridgeCan.overflowCount(),
        rioBridgeCan.overflowCount() > 0 ? "  <-- frames have been DROPPED, not just delayed" : "");

    attitudeFramesSinceLastPrint = 0;
    previousDrivetrainReading = drivetrain;
    previousRioBridgeReading = rioBridge;
  }

  private static void printReading(
      BusHealthMonitor.BusReading current, BusHealthMonitor.BusReading previous) {
    if (current == null) {
      return; // sampleSafely already explained why.
    }
    boolean regressed = previous != null && BusHealthMonitor.regressed(previous, current);
    System.out.println(BusHealthMonitor.describe(current) + flag(regressed));
  }

  /**
   * {@code CANJNI.getCANStatus} can throw at the HAL layer for a bus this process has never
   * otherwise touched -- confirmed on real hardware for the drivetrain bus in a run of exactly
   * this class, where nothing here ever constructs a device or opens a session on it (that
   * happens in the real robot code this class temporarily replaces, not here). Its SocketCAN
   * interface simply not being up yet on the Core is the other plausible cause. Reported instead
   * of crashing the whole diagnostic and losing visibility into the other bus -- which matters
   * most exactly when it's the drivetrain bus that's failing, since the RioBridge bus is the one
   * this class's own {@link RioBridgeCan} session already guarantees is touched.
   */
  private static BusHealthMonitor.BusReading sampleSafely(CANPort bus) {
    try {
      return BusHealthMonitor.sample(bus);
    } catch (RuntimeException e) {
      System.out.println(
          bus
              + ": status unavailable ("
              + e.getMessage()
              + "). Likely means this bus isn't brought up on the Core yet, or nothing in this"
              + " process has used it -- doesn't necessarily mean anything is wrong with the bus"
              + " itself.");
      return null;
    }
  }

  private static String flag(boolean regressed) {
    return regressed ? "  <-- REGRESSED since last second" : "";
  }
}
