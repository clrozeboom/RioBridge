package frc.robot.subsystems.drive.riobridge.diagnostics;

import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.hal.can.CANJNI;
import org.wpilib.hardware.hal.can.CANStatus;

/**
 * Root README "to verify" item 3: whether the RioBridge's bus (CAN_S1) keeps up while bus 0 (the
 * drivetrain's SPARK MAXes + PDH) is loaded, given both HAT channels share the Pi's SPI master.
 *
 * <p>Wraps {@code CANJNI.getCANStatus} -- exactly the counters the README names. See
 * docs/hardware-verification.md for how to run this and what the numbers mean;
 * {@link DiagnosticsRobot} is a ready driver for it.
 */
public final class BusHealthMonitor {
  private BusHealthMonitor() {}

  /** One {@code getCANStatus} reading, tagged with which bus it came from. */
  public record BusReading(
      CANPort bus,
      double percentBusUtilization,
      int busOffCount,
      int txFullCount,
      int receiveErrorCount,
      int transmitErrorCount) {}

  public static BusReading sample(CANPort bus) {
    CANStatus status = new CANStatus();
    CANJNI.getCANStatus(bus.value, status);
    return new BusReading(
        bus,
        status.percentBusUtilization,
        status.busOffCount,
        status.txFullCount,
        status.receiveErrorCount,
        status.transmitErrorCount);
  }

  /**
   * True if {@code current}, compared to {@code previous} on the same bus, shows anything that
   * indicates lost headroom since the last sample: a new bus-off event, a new TX-full event, or
   * new receive/transmit errors. {@code percentBusUtilization} alone isn't a pass/fail signal (a
   * busy-but-healthy bus can sit at high utilization) -- these event counters are.
   */
  public static boolean regressed(BusReading previous, BusReading current) {
    return current.busOffCount() > previous.busOffCount()
        || current.txFullCount() > previous.txFullCount()
        || current.receiveErrorCount() > previous.receiveErrorCount()
        || current.transmitErrorCount() > previous.transmitErrorCount();
  }

  public static String describe(BusReading reading) {
    return String.format(
        "%s: util=%.1f%% busOff=%d txFull=%d rxErr=%d txErr=%d",
        reading.bus(),
        reading.percentBusUtilization(),
        reading.busOffCount(),
        reading.txFullCount(),
        reading.receiveErrorCount(),
        reading.transmitErrorCount());
  }
}
