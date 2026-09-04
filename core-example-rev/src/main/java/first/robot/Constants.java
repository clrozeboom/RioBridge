package first.robot;

public final class Constants {
  public static final class TestSubsystemConstants {
    private TestSubsystemConstants() {}

    /** SPARK MAX device ID on the CAN bus below. Arbitrary for this smoke test. */
    public static final int MOTOR_DEVICE_ID = 33;

    /**
     * CAN_S0, matching the root README's topology (bus 0 carries the drivetrain's SPARK MAXes +
     * PDH; bus 1/CAN_S1 is the RioBridge's own bus and has no motor controllers on it -- ADR-0002).
     */
    public static final org.wpilib.hardware.bus.CANPort MOTOR_BUS =
        org.wpilib.hardware.bus.CANPort.CAN_S0;

    public static final double CLOCKWISE_OUTPUT = 0.3;
    public static final double COUNTERCLOCKWISE_OUTPUT = -0.3;
    public static final int SMART_CURRENT_LIMIT_AMPS = 40;
  }

  private Constants() {}
}
