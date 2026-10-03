# ESP32 Studio implementation checklist

This checklist records code that is actually present in the repository.

## Core Android workflow

- [x] Kotlin Android application.
- [x] USB host support.
- [x] USB permission flow.
- [x] Automatic USB serial discovery.
- [x] Automatic selection of the strongest supported device.
- [x] Automatic serial connection after USB permission.
- [x] 115200 serial monitor.
- [x] Local project storage.
- [x] Project save command.
- [x] Project-local Arduino FQBN selection.
- [x] Arduino board/core search and installation commands.
- [x] Generic Arduino FQBN compilation.

## Arduino board coverage

- [x] Official Arduino Boards Manager package model.
- [x] ESP32 Arduino core configuration.
- [x] Classic AVR core support (`arduino:avr`) for compilation.
- [x] Native Android STK500v1 uploader for classic Uno/Nano targets.
- [x] Generic desktop Arduino CLI upload path for any installed compatible core.
- [ ] Native Android uploader adapter for every possible Arduino core.
- [ ] Physical Uno/Nano upload verification.
- [ ] Physical upload verification for additional Arduino cores.

## ESP32-native control

- [x] Native ROM bootloader protocol implementation.
- [x] SLIP packet encoding.
- [x] ESP checksum implementation.
- [x] Automatic DTR/RTS bootloader reset sequence.
- [x] Bootloader SYNC probe.
- [x] Binary flash write with FLASH_BEGIN / FLASH_DATA / FLASH_END.
- [x] Flash progress reporting.
- [x] Project-path safety check for firmware files.
- [x] Binary firmware file check.
- [x] 0x1000 flash-address alignment check.
- [ ] Flash-content hash verification.
- [ ] Chip revision and flash-size probing.
- [ ] Multi-image flash manifest.
- [ ] Physical ESP32 flash verification.

## Desktop toolchains

- [x] Windows/Ubuntu Arduino CLI bridge.
- [x] Windows/Ubuntu board discovery bridge.
- [x] Windows/Ubuntu Arduino core installation bridge.
- [x] Windows/Ubuntu Arduino compile/upload/monitor bridge.
- [x] Windows/Ubuntu ESP-IDF build/flash/monitor bridge.
- [ ] Full Windows ESP-IDF installation smoke test.
- [ ] Full Ubuntu ESP-IDF installation smoke test.

## Libraries and projects

- [x] Official Arduino Library Registry search with a 24-hour streaming index cache.
- [x] Library archive download with size limits and SHA-256 verification when supplied by the registry.
- [x] Safe ZIP extraction with path traversal checks.
- [x] Install and replace a library under the app-private Arduino sketchbook.
- [x] List installed libraries.
- [ ] Dependency lock file.
- [ ] Multiple project browser.
- [ ] Git integration.

## Wireless and advanced tooling

- [ ] OTA discovery.
- [ ] OTA upload.
- [ ] Android ESP-IDF backend.
- [ ] Optional remote build backend.
- [ ] Full PTY terminal.

## Verification gate

- [x] Source reviewed after implementation.
- [x] Protocol helper unit tests added.
- [x] Command runner unit tests added.
- [x] Board FQBN unit tests added.
- [x] Desktop wrapper unit test added.
- [ ] Android CI build passes after the latest commits.
- [ ] Physical ESP32 probe tested on hardware.
- [ ] Physical ESP32 flash tested on hardware.
- [ ] Physical Uno/Nano flash tested on hardware.

Hardware and toolchain verification must be reported as incomplete until performed on the corresponding platform.
