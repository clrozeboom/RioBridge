package frc.robot;

/**
 * The RioBridge's one attitude sensor, kept behind a seam so {@link Robot} doesn't depend on the
 * navX vendor library directly. {@link NavxAttitudeSource} is the real implementation; a fake
 * implementation is what makes {@link Robot}'s CAN-packing logic exercisable without the vendor
 * jar present (see rio-bridge/README.md).
 */
public interface AttitudeSource {
  boolean isConnected();

  /** Degrees, RioBridge/navX sign convention -- not yet reconciled with the Core's. */
  double getYawDeg();

  double getYawRateDegPerSec();

  double getPitchDeg();

  double getRollDeg();
}
