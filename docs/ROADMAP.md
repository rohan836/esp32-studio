# Roadmap

## 0.1 — foundation

- Android app
- USB host support
- automatic serial device discovery
- USB permission flow
- serial monitor
- local project storage
- lightweight terminal
- unit tests
- CI

## 0.2 — build runtime

- Android-compatible Arduino CLI packaging
- ESP32 Arduino core packaging
- toolchain cache
- library manager
- build cache
- integrate the native bootloader flasher into the build pipeline

## 0.3 — one-tap workflow

- automatic chip revision and flash-size resolution
- automatic port selection
- compile -> flash -> serial
- clear failure recovery
- progress events
- flash-content verification

## 0.4 — project system

- file browser
- multiple projects
- Git
- templates
- dependency lock file

## 0.5 — wireless

- OTA discovery
- OTA upload
- Wi-Fi serial options where supported

## 1.0 — broader platform support

- ESP32
- ESP32-C3
- ESP32-S3
- ESP32-C6
- ESP-IDF backend
- optional remote build backend

## Current verification status

- Native ESP32 ROM probing and binary flashing are implemented.
- Unit tests cover the protocol encoding and command runner.
- Physical ESP32 flashing still requires hardware verification.
- Automatic source compilation on Android is still not implemented.

## Explicit non-goals for the first release

- full desktop IDE feature parity
- arbitrary native Linux binaries
- automatic flashing when board identity is unsafe or ambiguous
