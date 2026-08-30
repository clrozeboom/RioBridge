# The RioBridge gets a CAN bus to itself

A roboRIO running FRC robot code transmits the RIO heartbeat at arbitration ID `0x01011840`
every 20 ms, unconditionally, and FRC actuator devices are required to key their enable state off
it. A RioBridge sharing a bus with motor controllers would therefore be a second, uncoordinated
source of robot enable state. The RioBridge is given its own CAN segment, with nothing that
actuates on it, so the heartbeat it cannot suppress reaches nothing that would act on it.

## Consequences

This spends one of the Core's CAN buses, and means a RioBridge cannot be added to a single-bus
robot without a second CAN interface. Termination needs no extra parts: the roboRIO has an
internal 120 Ohm resistor across CAN L and H, and the CAN HAT channel terminates the other end.
