#!/usr/bin/env python3
"""ESP32 Studio desktop toolchain bridge.

Delegates to the official Arduino CLI and Espressif ESP-IDF installed on the host.
Supports Windows and Ubuntu/Linux.
"""

from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Sequence


def executable(name: str, env_name: str) -> str:
    explicit = os.environ.get(env_name)
    if explicit:
        return explicit
    value = shutil.which(name)
    if value:
        return value
    raise RuntimeError(
        f"{name} was not found. Set {env_name} or install the official toolchain."
    )


def run(command: Sequence[str], cwd: Path | None = None) -> int:
    print("$ " + " ".join(str(x) for x in command))
    return subprocess.run(command, cwd=str(cwd) if cwd else None).returncode


def arduino_cli() -> str:
    return executable("arduino-cli", "ESP_STUDIO_ARDUINO_CLI")


def idf_py() -> str:
    return executable("idf.py", "ESP_STUDIO_IDF_PY")


def fqbn_core(fqbn: str) -> str:
    parts = fqbn.strip().split(":")
    if len(parts) < 3 or any(not part for part in parts):
        raise ValueError(f"Invalid FQBN: {fqbn}")
    return ":".join(parts[:-1])


def command_doctor(_: argparse.Namespace) -> int:
    print(f"Host: {platform.system()} {platform.machine()}")
    print(f"Python: {sys.version.split()[0]}")
    failures = 0
    try:
        failures |= run([arduino_cli(), "version"])
        failures |= run([arduino_cli(), "core", "list"])
    except RuntimeError as error:
        failures += 1
        print(f"Arduino CLI: {error}")
    try:
        failures |= run([idf_py(), "--version"])
    except RuntimeError as error:
        failures += 1
        print(f"ESP-IDF: {error}")
    print("IDF_PATH:", os.environ.get("IDF_PATH", "<not set>"))
    return 1 if failures else 0


def command_boards(_: argparse.Namespace) -> int:
    return run([arduino_cli(), "board", "list"])


def command_board_search(args: argparse.Namespace) -> int:
    return run([arduino_cli(), "board", "listall", args.query])


def command_core_install(args: argparse.Namespace) -> int:
    update = run([arduino_cli(), "core", "update-index"])
    if update:
        return update
    return run([arduino_cli(), "core", "install", args.core])


def command_core_list(_: argparse.Namespace) -> int:
    return run([arduino_cli(), "core", "list"])


def command_build(args: argparse.Namespace) -> int:
    project = Path(args.project).resolve()
    command = [
        arduino_cli(),
        "compile",
        "--fqbn",
        args.fqbn,
        "--output-dir",
        str(project / "build"),
        "--export-binaries",
        str(project),
    ]
    return run(command, cwd=project)


def command_upload(args: argparse.Namespace) -> int:
    project = Path(args.project).resolve()
    return run([
        arduino_cli(),
        "upload",
        "--fqbn",
        args.fqbn,
        "--port",
        args.port,
        str(project),
    ], cwd=project)


def command_monitor(args: argparse.Namespace) -> int:
    return run([
        arduino_cli(),
        "monitor",
        "--port",
        args.port,
        "--config",
        f"baudrate={args.baud}",
    ])


def command_idf_build(args: argparse.Namespace) -> int:
    project = Path(args.project).resolve()
    return run([idf_py(), "build"], cwd=project)


def command_idf_flash(args: argparse.Namespace) -> int:
    project = Path(args.project).resolve()
    return run([idf_py(), "-p", args.port, "flash"], cwd=project)


def command_idf_monitor(args: argparse.Namespace) -> int:
    project = Path(args.project).resolve()
    return run([idf_py(), "-p", args.port, "monitor"], cwd=project)


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="ESP32 Studio desktop toolchain bridge")
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor").set_defaults(func=command_doctor)
    sub.add_parser("boards").set_defaults(func=command_boards)
    search = sub.add_parser("board-search")
    search.add_argument("query")
    search.set_defaults(func=command_board_search)
    core_install = sub.add_parser("core-install")
    core_install.add_argument("core")
    core_install.set_defaults(func=command_core_install)
    sub.add_parser("core-list").set_defaults(func=command_core_list)
    build = sub.add_parser("build")
    build.add_argument("--fqbn", required=True)
    build.add_argument("project")
    build.set_defaults(func=command_build)
    upload = sub.add_parser("upload")
    upload.add_argument("--fqbn", required=True)
    upload.add_argument("--port", required=True)
    upload.add_argument("project")
    upload.set_defaults(func=command_upload)
    monitor = sub.add_parser("monitor")
    monitor.add_argument("--port", required=True)
    monitor.add_argument("--baud", default="115200")
    monitor.set_defaults(func=command_monitor)
    idf_build = sub.add_parser("idf-build")
    idf_build.add_argument("project")
    idf_build.set_defaults(func=command_idf_build)
    idf_flash = sub.add_parser("idf-flash")
    idf_flash.add_argument("--port", required=True)
    idf_flash.add_argument("project")
    idf_flash.set_defaults(func=command_idf_flash)
    idf_monitor = sub.add_parser("idf-monitor")
    idf_monitor.add_argument("--port", required=True)
    idf_monitor.add_argument("project")
    idf_monitor.set_defaults(func=command_idf_monitor)
    return p


if __name__ == "__main__":
    args = parser().parse_args()
    raise SystemExit(args.func(args))
