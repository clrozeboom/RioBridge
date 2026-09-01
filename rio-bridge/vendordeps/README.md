# vendordeps

`Studica.json` pins the navX2 vendor library: `com.studica.frc:Studica-java:2026.0.0`, from
`https://dev.studica.com/maven/release/2026/`. `NavxAttitudeSource.java`'s use of it has been
compiled and run under the desktop HAL sim against this exact version -- see
`../README.md`'s "What's verified".

To update it: WPILib VS Code extension -> `WPILib: Manage Vendor Libraries` -> `Install new
library (online)`, or edit the `version` field here directly if you just need a point release.
