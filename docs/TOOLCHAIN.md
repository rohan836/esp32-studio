# Android toolchain status

## Implemented in the repository

- The Android CI workflow builds the Android ARM64 Arduino CLI fork and packages it as an executable in the APK's native-library directory.
- The app configures Arduino CLI with app-private data, download, sketchbook, and library directories.
- The ESP32 board package index is configured.
- The app has commands to install the ESP32 Arduino core, compile the current sketch, and report toolchain status.
- The Library Manager searches the official Arduino Library Registry, downloads archives, checks SHA-256 when the registry supplies it, blocks ZIP path traversal, and installs libraries under the app-private sketchbook.

Commands:
- `esp toolchain status`
- `esp toolchain install`
- `esp build`
- `esp lib search SSD1306`
- `esp lib add Adafruit SSD1306`
- `esp lib list`

## Runtime limitation that must be verified

The Arduino CLI executable is built for Android ARM64. The ESP32 board package downloads additional compiler and linker executables. The experimental upstream fork documents Android/Termux package installation, but that does not prove every downloaded compiler tool runs inside a normal Android application sandbox.

The code must report compiler launch failures. Do not mark local ESP32 compilation as verified until the complete install-and-build path succeeds on a normal Android app installation.

## Firmware flashing limitation

The native ROM flasher can write a prebuilt binary to a selected address. A full clean-board flash usually requires a bootloader image, partition table, and application image at their correct addresses. Do not treat a single application binary at 0x10000 as a complete factory flash for every board.

A real USB-connected ESP32 is required to verify physical flashing and automatic reset behavior. Protocol unit tests do not replace that hardware test.
