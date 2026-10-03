# ESP32 Studio desktop toolchain

The desktop backend uses the official host toolchains instead of shipping private compiler forks.

## Windows

Install the official Arduino CLI and the Espressif ESP-IDF Installation Manager (EIM), then open an ESP-IDF terminal so `idf.py` is on PATH.

Run:

```powershell
py desktop/esp_studio.py doctor
py desktop/esp_studio.py boards
py desktop/esp_studio.py board-search uno
py desktop/esp_studio.py core-install arduino:avr
py desktop/esp_studio.py build --fqbn arduino:avr:uno path\to\sketch
py desktop/esp_studio.py upload --fqbn arduino:avr:uno --port COM5 path\to\sketch
py desktop/esp_studio.py idf-build path\to\idf-project
py desktop/esp_studio.py idf-flash --port COM5 path\to\idf-project
py desktop/esp_studio.py idf-monitor --port COM5 path\to\idf-project
```

## Ubuntu

Install Arduino CLI and ESP-IDF with the official Espressif EIM. Activate the ESP-IDF environment before using the IDF commands.

Run:

```bash
python3 desktop/esp_studio.py doctor
python3 desktop/esp_studio.py boards
python3 desktop/esp_studio.py board-search uno
python3 desktop/esp_studio.py core-install arduino:avr
python3 desktop/esp_studio.py build --fqbn arduino:avr:uno ./sketch
python3 desktop/esp_studio.py upload --fqbn arduino:avr:uno --port /dev/ttyACM0 ./sketch
python3 desktop/esp_studio.py idf-build ./idf-project
python3 desktop/esp_studio.py idf-flash --port /dev/ttyACM0 ./idf-project
python3 desktop/esp_studio.py idf-monitor --port /dev/ttyACM0 ./idf-project
```

The Arduino side is FQBN-driven. That means the backend can use the Arduino platform/core currently installed by Arduino CLI rather than a fixed board list.

For ESP-IDF, the backend delegates to `idf.py`, which is the official Espressif build/flash/monitor entry point.
