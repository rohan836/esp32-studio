# ESP32 Studio CLI design

The CLI is task-oriented. Users should not need to remember long compiler commands for common actions.

## Commands

### Device

    esp devices
    esp detect
    esp info
    esp doctor

### Build

    esp build
    esp build --clean
    esp build --verbose

### Flash

    esp flash
    esp run

### Serial

    esp monitor
    esp monitor --baud 115200

### Libraries

    esp lib search NAME
    esp lib add NAME
    esp lib remove NAME
    esp lib list

### Project

    esp project new
    esp project open
    esp project clean

### Network

    esp ota discover
    esp ota flash
    esp ota monitor

## Automatic behaviour

esp run should:

1. Save the project.
2. Check dependencies.
3. Detect the best candidate device.
4. Probe the chip.
5. Resolve the board profile.
6. Build only what changed.
7. Refuse upload if the build failed.
8. Flash at the safe configured speed.
9. Reset the device.
10. Open the serial monitor.

## Errors

Errors should have two levels.

User message:

    Build failed: library WiFi.h was not found.
    Install the missing library?

Advanced details:

    Full compiler output
    Command
    Exit code
    Toolchain version

This keeps the common workflow simple without removing diagnostic information.

## Extensibility

Each command should call a service interface rather than execute ad-hoc shell strings.

Example:

    DeviceService
    BuildService
    FlashService
    LibraryService
    SerialService

This makes automatic behaviour testable and prevents the UI from becoming a collection of shell commands.
