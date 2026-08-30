# The RioBridge is read-only, and outputs are excluded rather than deferred

Driving outputs would turn the RioBridge from a sensor node into an actuator controller,
requiring enable/disable semantics, a command-timeout failsafe, and defined behaviour on lost
communications — and would reintroduce the heartbeat problem of ADR-0002 in its most dangerous
form. A read-only RioBridge fails safe by going quiet. Outputs are therefore excluded from the
protocol entirely, so no complexity is paid for a capability that may never be built; adding them
later is a new design, not an extension of this one.

## Consequences

Nothing on the RioBridge should instantiate a motor controller. Note also that a roboRIO with no
Driver Station attached holds PWM, relay and solenoid outputs disabled at the FPGA watchdog
regardless, so outputs would never have been a matter of simply calling `set()`.
