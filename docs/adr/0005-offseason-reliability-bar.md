# The reliability bar is "good enough to test things", not competition-grade

This project targets an offseason robot used for pre-season testing. It will not be played in a
match, and is not intended to be rule-legal for competition. The bar for failure handling is
therefore diagnosability, not survivability: it must be obvious when the RioBridge link is
degraded, and it must not fail in a way that damages hardware or silently corrupts data, but it
does not need to carry a robot through the last thirty seconds of an elimination match.

Recorded because the code contains no marker of this and the natural instinct when reading a CAN
protocol with failure semantics is to harden it further. Blending on gyro reconnect, interpolated
sample alignment, redundant liveness paths and similar were all considered and deliberately left
out on these grounds, not by oversight.

## Consequences

The safety-shaped decisions in ADR-0002 (dedicated bus) and ADR-0003 (read-only) still stand,
because those prevent a RioBridge from disrupting motor controllers or the test robot itself,
which matters on a bench exactly as much as on a field.
