# The RioBridge sends every frame explicitly, never with writePacketRepeating

WPILib's `writePacketRepeating` hands a payload to the CAN driver, which retransmits it at a
fixed interval independently of the robot program. Used for RioBridge telemetry this would mean
that a hung loop or a failing sensor read produces perfectly regular frames carrying stale
values: staleness becomes undetectable at the Core, which is a worse failure than the RioBridge
falling silent. Every frame is therefore sent explicitly from the RioBridge's periodic loop, so
that the arrival of a frame is itself evidence that the loop ran and the sensor was read.

## Consequences

Frame timing inherits the RioBridge loop's jitter, which is acceptable because the Core timestamps
each frame on arrival (`CANReceiveMessage.timestamp`, or `CANStreamMessage.timestamp` for the
buffered stream session an earlier design used — see core-integration/README.md for which API the
Core side reads through now). It also removes the need for a per-frame sequence counter — a
dropped frame shows as a doubled interval between timestamps — which is what lets the fast frames
use all 8 data bytes.
