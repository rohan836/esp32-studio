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

This repository now contains the Android foundation for ESP32 Studio.

Working foundation:

- Native Kotlin Android app.
- Automatic USB serial discovery.
- ESP32 and common USB-UART device classification.
- Android USB permission handling.
- Automatic connect and 115200 serial monitor.
- Local project storage with a starter sketch.
- Lightweight Android shell terminal.
- Arduino CLI adapter for future local toolchain installation.
- Unit tests for device classification and command execution.
- GitHub Actions build configuration.

Not yet complete:

- Android-compatible local Arduino compiler/toolchain packaging.
- Native ESP bootloader flashing implementation.
- Automatic chip-level probing and safe board-profile resolution.
- Library download UI.
- Incremental firmware build cache.
- OTA support.
- ESP-IDF backend.

The project does not pretend these parts are finished. The main engineering risk is the Android-compatible compiler runtime. A desktop Linux Arduino CLI package cannot be assumed to run on Android.

## First user workflow

1. Open the app.
2. Connect an ESP32 with a USB OTG connection.
3. ESP32 Studio detects the USB serial device.
4. Android asks for USB permission when required.
5. The app opens the serial monitor automatically.
6. Edit and save the project.
7. The future build runtime will compile, flash, and reopen the serial monitor with one action.

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
