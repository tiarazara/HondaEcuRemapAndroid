# Honda ECU Remap Android — Alpha 0.6

Android Kotlin project for offline BIN/XDF map viewing and editing.

## Alpha 0.6 updates
- RPM and TPS linked breakpoint headers for maps whose XDF axes link to breakpoint tables.
- Scrollable calibration grid.
- Rectangular block selection, clipboard copy, and multi-cell paste (TAB-separated columns, newline-separated rows).
- XDF engineering-value conversion for supported linear equations.
- Save edits to a new BIN file; the input file is not overwritten.

## Build
Open in Android Studio or use the included GitHub Actions workflow to build a debug APK.

## Important limitations
This is an offline map editor prototype, not a complete ECU tuning/flash tool. Some XDF axis definitions and nonlinear equations are not supported. FTDI detection only identifies a USB device; it does not communicate with the ECU. No checksum correction, ECU read/write, DTC scanning, live data, flashing, or recovery is implemented. Validate values, axes, block editing, and saved BIN against TunerPro using duplicate files. Do not flash edited files until the ECU protocol, checksum, readback, and recovery process are independently validated.


## Alpha 0.7 read-only viewer update
- Improved vertical gesture handling inside the map grid so the outer page is less likely to intercept table scrolling.
- Increased the map viewport height.
- This safe preview build is read-only: map cells cannot be edited and modified BIN export controls are omitted.
- No ECU flashing or live ECU communication is implemented or validated.
