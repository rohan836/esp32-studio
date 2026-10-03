# ESP32 Studio implementation checklist

This checklist records code that is actually present in the repository.

## Core Android workflow

- [x] Kotlin Android application.
- [x] USB host support.
- [x] USB permission flow.
- [x] Automatic USB serial discovery.
- [x] Automatic selection of the strongest ESP32 candidate.
- [x] Automatic serial connection after USB permission.
- [x] 115200 serial monitor.
- [x] Local project storage.
- [x] Project save command.
- [x] ESP32-specific command parser.
- [x] Android shell fallback.

## ESP32-native control

- [x] Native ROM bootloader protocol implementation.
- [x] SLIP packet encoding.
- [x] ESP checksum implementation.
- [x] Automatic DTR/RTS bootloader reset sequence for common UART boards.
- [x] Bootloader SYNC probe.
- [x] Binary flash write with FLASH_BEGIN / FLASH_DATA / FLASH_END.
- [x] Flash progress reporting.
- [x] Project-path safety check for firmware files.
- [x] Binary firmware file check.
- [x] 0x1000 flash-address alignment check.
- [ ] Flash-content hash verification.
- [ ] Chip revision and flash-size probing.
- [ ] Multi-image flash manifest.

## Build system

- [x] Arduino CLI adapter interface.
- [x] Safe process timeout and output handling.
- [x] Unit tests for command timeout and stdout/stderr capture.
- [ ] Android-compatible Arduino compiler runtime.
- [ ] ESP32 Arduino core package manager.
- [ ] Incremental build cache.
- [ ] One-command source -> build -> flash -> monitor.

## Libraries and projects

- [ ] Library search.
- [ ] Library download/install.
- [ ] Dependency lock file.
- [ ] Multiple project browser.
- [ ] Git integration.

## Wireless and advanced tooling

- [ ] OTA discovery.
- [ ] OTA upload.
- [ ] ESP-IDF backend.
- [ ] Optional remote build backend.
- [ ] Full PTY terminal.

## Verification gate

- [x] Source reviewed after implementation.
- [x] Protocol helper unit tests added.
- [x] Command runner unit tests added.
- [ ] Android CI build passes after the latest commits.
- [ ] Physical ESP32 probe tested on hardware.
- [ ] Physical binary flash tested on hardware.

The last two hardware checks need a real ESP32 connected to an Android device. They cannot be honestly marked complete from repository inspection alone.
