# vendordeps

Empty on purpose. This project needs one vendor library -- Studica's navX2 -- and this repo
doesn't ship its vendordep JSON here, because the URL for the 2026 release couldn't be confirmed
from this sandbox while writing the project (Studica hosts their own vendordep Maven repo at
`dev.studica.com`; none of the plausible `/releases/2026/...` paths resolved, which just as
plausibly means the real path has a different shape than guessed, not that no 2026 release
exists).

Before building, add it the normal WPILib way:

1. WPILib VS Code extension -> `WPILib: Manage Vendor Libraries` -> `Install new library
   (online)` -> search for "navX" or "Studica".
2. Confirm it lands here as a `.json` file (that's all "installing" a vendordep does -- the
   actual jars resolve from Maven at build time, same as `wpi.java.vendor.java()` already expects
   in `../build.gradle`).
3. Open `../src/main/java/frc/robot/NavxAttitudeSource.java` and confirm the two things flagged
   in its class javadoc against the version you installed.
