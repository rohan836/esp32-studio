# ESP32 Studio

A lightweight Android development environment for ESP32 boards.

## Goals

- Detect supported ESP32 USB devices automatically.
- Avoid manual serial-port selection in the normal workflow.
- Keep the UI responsive during USB I/O, builds, downloads, and flashing.
- Provide a small code editor, project storage, terminal, and serial monitor.
- Use cached toolchains and libraries.
- Refuse unsafe flash choices instead of guessing.

## Current status

The repository now contains the Android foundation plus a native ESP32 ROM bootloader path.

Working foundation:

- Native Kotlin Android app.
- Automatic USB serial discovery and selection.
- ESP32 and common USB-UART device classification.
- Android USB permission handling.
- Automatic serial connection and 115200 monitor.
- Local project storage with a starter sketch.
- ESP32-specific command layer with shell fallback.
- Native ESP32 bootloader SYNC probing.
- Native binary flash writing with progress reporting.
- Safe project-path and flash-address checks.
- Unit tests for device classification, CLI parsing, protocol encoding, and command execution.
- GitHub Actions build and test configuration.

Not yet complete:

- Android-compatible local Arduino compiler/toolchain packaging.
- Automatic chip revision and flash-size probing.
- Flash-content verification.
- Library search/download/install.
- Incremental firmware build cache.
- OTA support.
- ESP-IDF backend.

Native flashing accepts a prebuilt .bin image. The repository does not claim that physical ESP32 flashing has been verified until hardware testing is performed. The remaining major engineering gap is the Android-native build runtime; a desktop Linux Arduino CLI package cannot be assumed to run on Android.

## First user workflow

1. Open the app.
2. Connect an ESP32 with a USB OTG connection.
3. ESP32 Studio detects the USB serial device.
4. Android asks for USB permission when required.
5. The app opens the serial monitor automatically.
6. Edit and save the project.
7. Probe the ESP bootloader with `esp info` when hardware access needs verification.
8. Flash a compiled image with `esp flash firmware.bin 0x10000`.
9. Return to the automatic serial monitor.

## Architecture

- app/ — Android application.
- docs/ — architecture, toolchain, roadmap, and security notes.
- sample/ — example ESP32 sketch.
- .github/workflows/ — CI.

## Design rule

The normal workflow should not expose COM ports, compiler paths, package paths, or board IDs.

The app should detect these values where possible. Advanced controls remain available only when automatic detection cannot safely decide.

## Hardware support

USB serial transport uses usb-serial-for-android 3.11.0. That project supports CDC/ACM and common USB-UART chips such as FTDI, CP210x, CH340/CH341, and PL2303. It also supports Espressif native USB serial devices where the USB interfaces match its supported drivers.

Chip-level identity and flash configuration still need a dedicated ESP bootloader probe before automatic flashing is enabled.

## Build

Use Android Studio with JDK 17.

The current CI configuration installs Gradle 9.4.1 and Android SDK API 37, then builds the debug APK and runs unit tests.

## License

MIT
