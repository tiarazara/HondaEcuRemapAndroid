# Honda ECU Remap Android — Alpha 0.3

A simple Android project for opening a Honda ECU BIN and its TunerPro XDF definition,
viewing table data, editing raw HEX bytes, exporting a separate BIN copy, and checking
for an attached FTDI USB device.

## Open in Android Studio
1. Extract this ZIP.
2. Open the `HondaEcuRemapAndroid` folder in Android Studio.
3. Allow Gradle sync (Android Gradle Plugin and Kotlin plugin must be available).
4. Connect the Xiaomi phone with USB debugging enabled, or use an emulator.
5. Build > Build APK(s).

## Basic use
1. Tap **BUKA FILE BIN** and select the original BIN.
2. Tap **MUAT FILE XDF** and select the matching XDF.
3. Select a map in the list.
4. Tap a cell to edit its raw HEX bytes.
5. Tap **SIMPAN SALINAN BIN** to export a new file.

The app never overwrites the selected source BIN. The save action exports the current
in-memory bytes as a separate file.

## Important technical limits
This alpha displays and edits raw bytes. It does not yet evaluate XDF math equations,
convert to engineering units, infer 16-bit endianness, calculate ECU checksums, or
implement the ECU's serial flashing protocol. USB detection is not the same as a
working serial connection. Do not flash a modified file until those items are
implemented and validated against the user's known-good laptop workflow.

Target configuration: minSdk 26, compileSdk 35, targetSdk 32.

## Build APK otomatis lewat GitHub Actions

1. Buat repository GitHub baru bernama `HondaEcuRemapAndroid`.
2. Upload seluruh isi folder proyek ini ke repository (pastikan folder `.github/workflows` ikut ter-upload).
3. Buka tab **Actions** pada repository. Jika diminta, pilih **I understand my workflows, go ahead and enable them**.
4. Pilih workflow **Build Honda ECU Remap APK**, lalu tekan **Run workflow**. Bisa juga otomatis berjalan setelah file di-push ke branch `main`.
5. Setelah proses berstatus hijau, buka hasil run tersebut, cari bagian **Artifacts**, lalu download `HondaEcuRemap-debug-apk`.
6. Ekstrak ZIP artifact. File `app-debug.apk` adalah APK untuk dipasang di Android.

Catatan: build otomatis hanya mengompilasi source yang ada. Ini belum berarti komunikasi FTDI atau flashing ECU sudah tersedia/teruji.
