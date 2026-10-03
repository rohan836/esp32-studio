# ESP32 Studio vs Termux

## Problem

Termux is a general Android terminal and Linux environment. ESP32 Studio has a narrower goal: fast embedded development with automatic hardware detection.

Termux is the better base for a general Linux shell. ESP32 Studio should be better at the ESP32 workflow.

## Termux strengths

Termux provides a real terminal application and Linux environment. Its package system provides installable command-line packages. It is designed for general shell work, not only ESP32 development.

Termux can be used as an external execution backend through its RUN_COMMAND interface when the user enables external command access.

Android can still restrict or kill processes under some conditions, so long-running work must be designed carefully.

## ESP32 Studio strengths

The normal workflow should not require the user to know:

- USB device names
- serial port names
- ESP chip names
- board identifiers
- compiler paths
- library paths
- flash addresses

ESP32 Studio should detect these values when the hardware exposes enough information to make a safe decision.

## Product decision

ESP32 Studio is not a replacement for Termux.

It is a specialized development environment with a task-oriented command line.

The terminal should have two layers:

### 1. Fast ESP32 command layer

Examples:

    esp detect
    esp info
    esp build
    esp flash
    esp run
    esp monitor
    esp devices
    esp lib search NAME
    esp lib add NAME
    esp clean
    esp doctor
    esp ota
    esp logs

These commands should use automatic device discovery and project configuration.

### 2. Advanced shell layer

Provide a real interactive shell with:

- PTY support
- stdin/stdout/stderr
- Ctrl-C
- Ctrl-D
- terminal resize
- environment variables
- working directories
- command history
- pipes and redirection
- long-running processes

Do not pretend that /system/bin/sh -c is a full Linux development environment. It is only a shell entry point.

## Automatic detection pipeline

    USB attach
        |
        v
    enumerate devices
        |
        v
    identify USB transport
        |
        v
    locate candidate ESP device
        |
        v
    connect to bootloader
        |
        v
    detect chip
        |
        v
    read flash/device information
        |
        v
    resolve project profile
        |
        v
    validate build target
        |
        v
    build
        |
        v
    flash
        |
        v
    start serial monitor

If a safe value cannot be resolved, stop and show the missing information. Never guess flash configuration.

## Performance rules

- Detect USB events instead of polling continuously.
- Keep toolchains warm after installation.
- Cache board cores and libraries.
- Cache build outputs.
- Run compiler and flash work outside the UI thread.
- Stream process output instead of buffering large logs.
- Keep terminal history bounded.
- Avoid starting a Linux environment for simple ESP32 actions.
- Start the full shell only when the user opens Terminal.

## What should be faster than Termux

Not general Linux commands.

The goal is lower interaction cost for ESP32 development.

Example:

### General Termux workflow

    inspect devices
    identify candidate serial device
    identify port
    identify board
    run build command
    run upload command
    open serial monitor

### ESP32 Studio workflow

    esp run

The app resolves the device, project target, build command, upload path, and monitor automatically.

## What should remain optional

- Full Linux package ecosystem
- Python environment
- Git
- SSH
- advanced shell tools
- remote Linux build backend

These can be installed or connected when needed.

## Licensing note

Do not copy Termux GPLv3 application code into this MIT repository without resolving the license requirements. Prefer compatible libraries, clean-room interfaces, or an external Termux integration where appropriate.

## Target architecture

Android UI
    |
    +-- ESP32 command layer
    |     +-- device discovery
    |     +-- chip detection
    |     +-- build
    |     +-- flash
    |     +-- library manager
    |     +-- serial monitor
    |
    +-- terminal layer
          +-- PTY
          +-- shell
          +-- history
          +-- pipes
          +-- environment

The ESP32 command layer is the product differentiator. The terminal layer is supporting infrastructure.
