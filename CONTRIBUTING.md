# Contributing

1. Keep UI code independent from device and toolchain code.
2. Do not add a manual COM-port selector to the default workflow.
3. Do not silently guess unsafe flash settings.
4. Keep long-running work off the Android main thread.
5. Add a unit test for new detection or command logic.
6. Keep the first-run APK small. Move large toolchains to installable payloads.
