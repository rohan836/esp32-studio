# ESP32 Studio CLI

ESP32 Studio exposes short, task-oriented commands for embedded development.

## Commands

    esp help
    esp devices
    esp info
    esp doctor
    esp project path
    esp build
    esp flash
    esp run
    esp monitor
    esp lib search <query>
    esp lib add <name>

Commands should resolve ports and board settings automatically. A command must not guess a flash layout when device evidence is incomplete.

The parser is a small native Kotlin component. It does not start a Linux environment just to parse an ESP32 command.

## Status

The command parser and tests are implemented. The command execution services are being connected incrementally. Build and flash commands must report unavailable toolchains explicitly until the local Android toolchain is installed.
