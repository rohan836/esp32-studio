# Toolchain architecture

## Android

Android uses the packaged Android ARM64 Arduino CLI runtime already present in the APK build.

The app configures:

- Arduino core indexes
- App-private Arduino data/download/sketchbook directories
- Project FQBN
- Library registry and library installation

The selected FQBN determines the core installed and the compiler invocation.

Android native upload adapters are intentionally explicit:

- ESP32 Arduino targets -> native ESP32 ROM bootloader writer
- `arduino:avr:uno` and classic Nano targets -> STK500v1 writer

Other boards can be compiled when their Arduino platform is compatible with the Android toolchain runtime, but their physical upload path is not falsely marked as implemented.

## Windows and Ubuntu

The desktop backend uses the official host tools installed by the user:

- Arduino CLI for Arduino-framework boards.
- Espressif ESP-IDF / `idf.py` for native ESP-IDF projects.

The bridge never substitutes its own compiler for these tools.

Typical flow:

```text
ESP32 Studio
    |
    +-- Arduino mode
    |     |
    |     +-- Arduino CLI
    |           |
    |           +-- Boards Manager core
    |           +-- compiler/tool dependencies
    |           +-- upload tool
    |           +-- monitor
    |
    +-- ESP-IDF mode
          |
          +-- ESP-IDF EIM installation
          +-- idf.py
          +-- CMake/Ninja/toolchain
          +-- esptool/monitor
```

See [desktop/README.md](../desktop/README.md) for commands.

## Verification boundary

A source implementation is not the same as a hardware-tested feature. The repository therefore keeps separate verification gates for:

1. compiler installation,
2. build,
3. upload,
4. serial monitor,
5. physical-board behavior.

Do not mark a platform as fully verified based only on unit tests.
