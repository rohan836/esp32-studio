# ESP32 Studio

ESP32 Studio is a small cross-platform development environment for Arduino-family boards and ESP32.

## Platform support

| Platform | Arduino build | Arduino upload | ESP32 Arduino | Native ESP-IDF |
|---|---|---|---|---|
| Android | Yes, through the packaged Android Arduino CLI runtime | ESP32 + classic AVR Uno/Nano native upload paths | Yes | No |
| Windows | Yes, official Arduino CLI | Yes, official Arduino CLI | Yes | Yes, official ESP-IDF |
| Ubuntu/Linux | Yes, official Arduino CLI | Yes, official Arduino CLI | Yes | Yes, official ESP-IDF |

The desktop backend intentionally delegates to the official host tools instead of shipping a second compiler implementation.

## Arduino board model

Boards are selected by **FQBN** (Fully Qualified Board Name), for example:

- `arduino:avr:uno`
- `arduino:avr:nano`
- `esp32:esp32:esp32`

The board/core manager uses Arduino's Boards Manager package system. A board core is installed from its core ID and the project stores the selected FQBN.

That means the software does not need a hard-coded "all boards" list. Any board exposed by the installed Arduino platform package can be selected and compiled.

Examples in the Android terminal:

```text
esp board listall uno
esp board set arduino:avr:uno
esp core install arduino:avr
esp build
esp run
```

For an ESP32:

```text
esp board set esp32:esp32:esp32
esp core install esp32:esp32
esp build
esp run
```

## Android

Implemented:

- USB Host detection and permission flow
- USB serial monitor
- Generic Arduino FQBN project target
- Arduino core search/list/install
- Generic Arduino compilation
- ESP32 ROM bootloader flash path
- AVR STK500v1 flash path for classic Uno/Nano targets
- Arduino Library Registry search/install
- Project-local board selection

Android does **not** claim native ESP-IDF support. Espressif's official ESP-IDF documentation targets desktop operating systems, so the Android implementation stays on the Arduino framework.

Physical board flashing still requires hardware validation. The repository cannot mark a USB flash path as verified without a real board test.

## Windows and Ubuntu

Use:

```text
desktop/esp_studio.py
desktop/esp-studio.ps1   # Windows
desktop/esp-studio.sh    # Ubuntu/Linux
```

The desktop bridge supports:

```text
doctor
boards
board-search
core-install
core-list
build
upload
monitor
idf-build
idf-flash
idf-monitor
```

Arduino commands delegate to the official Arduino CLI. ESP-IDF commands delegate to `idf.py` from the official Espressif installation.

See [desktop/README.md](desktop/README.md).

## Current verification state

Source-level implementation and unit tests are included.

The following still require environment/hardware verification:

- Android installation of downloaded compiler tool packages for every Arduino platform.
- Physical ESP32 flashing.
- Physical Uno/Nano flashing.
- Full ESP-IDF installation and build on Windows.
- Full ESP-IDF installation and build on Ubuntu.
- Board-specific upload behavior for Arduino cores other than the native Android ESP32/AVR adapters.

These are verification gates, not simulated success states.
