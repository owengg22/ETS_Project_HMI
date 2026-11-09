#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <time.h>
#include "root_ca.h"
#include <PubSubClient.h>
#include "config.h"

WiFiClientSecure transport;
char clientId[40];
PubSubClient mqtt(transport);
char line[192];
size_t used = 0;
bool dropping = false;
uint32_t sequence = 0, lastAttempt = 0;
const char *dataTopic = "owen/max30102/esp32-01/vitals";
const char *statusTopic = "owen/max30102/esp32-01/status";

void sendFrame() {
  unsigned digits = 0;
  for (const char *p = line; *p; ++p) {
    if (*p >= '0' && *p <= '9') { if (++digits > 9) return; }
    else digits = 0;
  }
  int hr, spo2, hv, sv, finger, end = 0;
  unsigned long red, ir;
  if (sscanf(line, "DATA,hr=%d,spo2=%d,hr_valid=%d,spo2_valid=%d,finger=%d,red=%lu,ir=%lu%n",
             &hr, &spo2, &hv, &sv, &finger, &red, &ir, &end) != 7 || line[end] != '\0') return;
  if (hv < 0 || hv > 1 || sv < 0 || sv > 1 || finger < 0 || finger > 1 || red > 262143 || ir > 262143) return;
  hv = hv && finger && hr > 0;
  sv = sv && finger && spo2 >= 0 && spo2 <= 100;
  char payload[320];
  snprintf(payload, sizeof(payload),
    "{\"device_id\":\"esp32-01\",\"seq\":%lu,\"uptime_ms\":%lu,\"hr\":%d,\"spo2\":%d,\"hr_valid\":%s,\"spo2_valid\":%s,\"finger\":%s,\"red\":%lu,\"ir\":%lu}",
    (unsigned long)++sequence, (unsigned long)millis(), hr, spo2,
    hv ? "true" : "false", sv ? "true" : "false", finger ? "true" : "false", red, ir);
  // ponytail: live QoS 0 only, no backlog or retained vitals; add storage for historical data.
  if (mqtt.connected() && !mqtt.publish(dataTopic, payload, false)) Serial.println("MQTT_PUBLISH_FAILED");
}

void setup() {
  Serial.begin(115200);
  Serial2.setRxBufferSize(4096);
  Serial2.begin(115200, SERIAL_8N1, 16, 17);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  transport.setCACert(ROOT_CA);
  transport.setHandshakeTimeout(3);
  const uint64_t chip = ESP.getEfuseMac();
  snprintf(clientId, sizeof(clientId), "owen-max-%08lx%04lx", (unsigned long)(chip >> 16), (unsigned long)(chip & 65535));
  mqtt.setBufferSize(512);
  mqtt.setSocketTimeout(1);
  mqtt.setKeepAlive(10);
  transport.setTimeout(1000);
  lastAttempt = millis() - 5000;
  Serial.println("ESP_READY,rx=16,tx=17,baud=115200,hivemq_tls=1,diag=2");
}

void loop() {
  // UART keeps draining while Wi-Fi is unavailable. Discard overlong frames until newline.
  while (Serial2.available()) {
    char c = (char)Serial2.read();
    Serial.write(c);
    if (c == '\r') continue;
    if (c == '\n') {
      line[used] = '\0';
      if (!dropping) sendFrame();
      used = 0;
      dropping = false;
    } else if (!dropping) {
      if (used < sizeof(line) - 1) line[used++] = c;
      else { dropping = true; used = 0; }
    }
  }
  const auto wifiStatus = WiFi.status();
  const bool wifiReady = wifiStatus == WL_CONNECTED;
  const bool clockReady = time(nullptr) > 1704067200;
  const unsigned long now = millis();
  static bool ntpStartedOnLink = false;
  static unsigned long lastNtpStart = 0;
  if (!wifiReady) {
    ntpStartedOnLink = false;
  } else if (!clockReady && (!ntpStartedOnLink || now - lastNtpStart >= 30000)) {
    ntpStartedOnLink = true;
    lastNtpStart = now;
    Serial.println("NTP_START");
    configTime(0, 0, "pool.ntp.org", "time.google.com", "time.nist.gov");
  }
  static unsigned long lastReport = 0;
  if (now - lastReport >= 5000) {
    lastReport = now;
    Serial.printf("NET_DIAG,wifi=%s,wifi_state=%d,ntp=%s,mqtt=%s\n",
                  wifiReady ? "UP" : "DOWN", static_cast<int>(wifiStatus),
                  clockReady ? "OK" : "WAIT", mqtt.connected() ? "UP" : "DOWN");
  }
  if (wifiReady && clockReady && !mqtt.connected() && now - lastAttempt >= 5000) {
    Serial.println("MQTT_CONNECTING");
    lastAttempt = millis();
    mqtt.setServer(MQTT_HOST, MQTT_PORT);
    const bool connected = mqtt.connect(clientId, MQTT_USER, MQTT_PASSWORD, statusTopic, 1, true, "offline");
    while (Serial2.available()) Serial2.read();
    used = 0; dropping = true;
    if (connected) {
      mqtt.publish(statusTopic, "online", true);
      Serial.print("MQTT_CONNECTED,ip=");
      Serial.println(WiFi.localIP());
    } else {
      Serial.printf("MQTT_ERROR,state=%d\n", mqtt.state());
    }
  }
  mqtt.loop();
  delay(1);
}
