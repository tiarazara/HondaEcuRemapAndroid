# Honda ECU Remap Android — Alpha 0.5

Android app project for loading a BIN and TunerPro XDF, listing XDF tables, displaying Z values through the simple linear XDF equations used in the supplied Scoopy K93-N01 definition, editing engineering values, exporting a separate BIN copy, and detecting an attached FTDI USB device.

## Build automatically with GitHub Actions
1. Upload the complete project folder contents to a GitHub repository (include `.github/workflows`).
2. Open **Actions** and run **Build Honda ECU Remap APK**, or push a commit to `main`.
3. Download the `HondaEcuRemap-debug-apk` artifact and extract `app-debug.apk`.

## Current limits
- Engineering conversion supports only simple linear XDF expressions of the form `X`, `X/divisor`, or `X/divisor*multiplier`; unsupported formulas are shown as raw values.
- 16-bit byte order is read from XDF `DEFAULTS lsbfirst`; the supplied XDF has `lsbfirst=0`. This interpretation still needs comparison against TunerPro using known table values before edited BIN files are trusted.
- Axis labels/breakpoint-linked axes are not yet rendered as full RPM/TPS headers.
- No checksum correction, ECU serial protocol, ECU read, DTC scanner, live data, flash, or recovery operation is implemented. USB FTDI detection only confirms the USB device is enumerated.
- Never flash an edited file until its values, byte order, checksum, and ECU protocol are independently validated against the known-good laptop workflow.

Target: minSdk 26, compileSdk 35, targetSdk 32, Java/Kotlin JVM 17.
