void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("Hello from ESP32 Studio");
}

void loop() {
  Serial.println("tick");
  delay(1000);
}
