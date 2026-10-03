# ESP32 Studio

A lightweight Android development environment for ESP32 boards.

## Goals

- Detect supported ESP32 devices automatically.
- Remove manual serial-port selection from the normal workflow.
- Build Arduino projects locally when the Android device supports the required tools.
- Flash firmware with safe device checks.
- Open the serial monitor after a successful upload.
- Cache toolchains, board packages, libraries, and build artifacts.
- Keep the user interface responsive while builds and uploads run in background workers.
- Keep advanced settings out of the normal workflow.

## Project status

The repository starts with the Android application foundation and the hardware-independent orchestration layer. Hardware support is implemented in small interfaces so USB, local build tools, and future wireless transports can be tested independently.

This project does not claim that every ESP32 board is supported automatically. Board detection is best-effort. The app must stop rather than guess when a flash configuration is unsafe.

## First milestone

1. Android application starts.
2. Project workspace can be opened.
3. USB devices are detected through Android's USB host API.
4. Supported serial devices can be opened after Android permission is granted.
5. Build and flash services expose a stable interface.
6. Arduino CLI/esptool integration can be added without changing the UI.
7. Unit tests cover detection and command orchestration.

## Repository layout

- `app/` — Android application.
- `core/` — hardware-independent domain and orchestration code.
- `docs/` — architecture and product notes.
- `.github/workflows/` — CI.
