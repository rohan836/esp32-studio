package com.esp32studio.project

import android.content.Context
import java.io.File

class ProjectStore(private val context: Context) {
    private val projectDir: File
        get() = File(context.filesDir, "projects/hello-esp32")

    private val sketchFile: File
        get() = File(projectDir, "hello-esp32.ino")

    fun load(): String {
        if (!sketchFile.exists()) {
            sketchFile.parentFile?.mkdirs()
            sketchFile.writeText(DEFAULT_SKETCH.trimIndent())
        }
        return sketchFile.readText()
    }

    fun save(source: String) {
        projectDir.mkdirs()
        sketchFile.writeText(source)
    }

    fun path(): File = projectDir

    fun boardFqbn(): String = "esp32:esp32:esp32"

    companion object {
        private const val DEFAULT_SKETCH = """
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("ESP32 Studio connected");
}

void loop() {
  Serial.println("hello from ESP32");
  delay(1000);
}
"""
    }
}
