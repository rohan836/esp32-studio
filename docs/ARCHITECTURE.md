# ESP32 Studio architecture

## Design rule

The Android UI does not know how a compiler or flash tool works.

It talks to small components:

- device discovery
- serial transport
- build toolchain
- command runner
- project storage

This lets the hardware layer change without redesigning the UI.

## Current implementation

### USB detection

Android is the USB host. ESP32 Studio scans attached serial drivers with usb-serial-for-android.

The app ranks devices:

1. Espressif or explicit ESP32 identity.
2. Known USB serial bridges commonly used by ESP32 boards.
3. Other serial devices.

Unknown devices are not treated as high-confidence ESP32 boards.

### USB permission

Android controls USB access. The app requests permission after it finds a usable serial device.

### Serial

The serial session runs outside the UI thread. It defaults to 115200 8-N-1 for ESP32 development.

### Build toolchain

ArduinoToolchain is an adapter around an Arduino CLI binary.

The adapter is separate from the UI because the main open engineering problem is providing Android-compatible compiler and board tools.

The first release does not pretend that a desktop Linux Arduino CLI binary will run correctly on Android.

## Target build flow

1. Detect USB device.
2. Request Android permission if needed.
3. Probe the chip.
4. Resolve the project board profile.
5. Check cached board core and libraries.
6. Compile incrementally.
7. Refuse to upload if the build fails.
8. Flash using the resolved transport.
9. Start the serial monitor.

## Performance

Long tasks must not run on the main thread.

Cache boundaries should include:

- toolchain packages
- ESP32 board cores
- Arduino libraries
- project build artifacts

The UI should update from task events instead of polling for progress.

## Safety

The app must not guess an unsafe flash configuration.

If board identity or flash layout is ambiguous, the app must stop and show the missing information.
