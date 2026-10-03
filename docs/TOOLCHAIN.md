# Toolchain plan

## Why the compiler is not bundled in 0.1

Arduino CLI is a command-line frontend. It also depends on board cores, compiler packages, and upload tools.

A desktop Linux Arduino CLI package cannot be assumed to run inside Android.

ESP32 Studio therefore keeps the toolchain behind ArduinoToolchain.

## Required runtime

The eventual local runtime needs:

- Arduino CLI compiled for the target Android ABI
- ESP32 board core
- compatible compiler binaries
- esptool
- package metadata
- cache storage

## Lightweight strategy

Install only the selected ESP32 family.

Keep tool packages under app-private storage.

Reuse them across builds.

Download libraries only when the project needs them.

## Fallback

If a fully local toolchain is not practical on a target Android device, ESP32 Studio can use a remote Linux build backend.

The UI remains the same.

The project is uploaded to the backend, compiled there, and the firmware is returned for USB or OTA upload.
