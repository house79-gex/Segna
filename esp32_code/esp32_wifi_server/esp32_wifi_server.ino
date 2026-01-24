/*
 * ESP32 WiFi HTTP Server
 * Segna Project - HTTP Server per controllo 5 LED singoli via WiFi
 *
 * Hardware:
 * - LED Bianco: GPIO 25 (Lettera A)
 * - LED Giallo: GPIO 26 (Lettera B)
 * - LED Verde: GPIO 27 (Lettera C)
 * - LED Rosso: GPIO 32 (Lettera D)
 * - LED Blu: GPIO 33 (Lettera E)
 *
 * Dipendenze:
 * - ArduinoJson (installare da Library Manager)
 * - WiFi (incluso in ESP32 core)
 * - WebServer (incluso in ESP32 core)
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include "config.h"

WebServer server(HTTP_PORT);

// Stato corrente
struct State {
  String letter = "";
  String color = "#000000";
  String colorName = "";
  String command = "";
  JsonDocument settings;
  unsigned long lastUpdate = 0;
} currentState;

// Variabili per gestione LED temporizzato
unsigned long ledStartTime = 0;
int ledDurationMs = 0;
bool ledTimerActive = false;
int currentActiveLed = -1; // -1 = nessun LED, 0-4 = LED attivo

void setup() {
  Serial.begin(115200);
  Serial.println("\n\nAvvio ESP32 WiFi HTTP Server...");
  
  // Configura pin LED come output
  pinMode(LED_WHITE_PIN, OUTPUT);
  pinMode(LED_YELLOW_PIN, OUTPUT);
  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_RED_PIN, OUTPUT);
  pinMode(LED_BLUE_PIN, OUTPUT);
  
  // Spegni tutti i LED all'avvio
  turnOffAllLeds();
  
  // Test LED all'avvio
  Serial.println("Test LED...");
  testLeds();
  
  // Connessione WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connessione a WiFi");
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n✅ WiFi connesso!");
    Serial.print("📡 IP Address: ");
    Serial.println(WiFi.localIP());
    Serial.print("📶 Signal Strength: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    Serial.println("\n❌ Connessione WiFi fallita!");
    Serial.println("⚠️ Verifica SSID e password in config.h");
    // Continua comunque per permettere debug via seriale
  }
  
  // Configura endpoint HTTP
  server.on("/send", HTTP_POST, handleSend);
  server.on("/receive", HTTP_GET, handleReceive);
  server.on("/status", HTTP_GET, handleStatus);
  
  // Abilita CORS per Flutter web (opzionale)
  server.enableCORS(true);
  
  server.begin();
  Serial.println("🌐 Server HTTP avviato sulla porta 80");
  Serial.println("📋 Endpoints disponibili:");
  Serial.println("   POST /send    - Riceve comandi da smartphone");
  Serial.println("   GET  /receive - Invia stato a watch");
  Serial.println("   GET  /status  - Pagina di debug");
  Serial.println("\n✅ Sistema pronto!");
}

void loop() {
  server.handleClient();
  
  // Gestione timer LED temporizzato
  if (ledTimerActive && (millis() - ledStartTime >= ledDurationMs)) {
    Serial.println("⏰ Timeout LED raggiunto, spegnimento...");
    turnOffAllLeds();
    ledTimerActive = false;
    currentActiveLed = -1;
  }
  
  delay(10);
}

// Riceve dati da smartphone (POST /send)
void handleSend() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No data\"}");
    return;
  }
  
  String body = server.arg("plain");
  Serial.println("\n📨 Ricevuto da smartphone:");
  Serial.println(body);
  
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, body);
  
  if (error) {
    Serial.print("❌ Errore parsing JSON: ");
    Serial.println(error.c_str());
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }
  
  // Gestione comando RESET
  if (doc.containsKey("command") && doc["command"] == "RESET") {
    Serial.println("🔄 Comando RESET ricevuto");
    
    currentState.command = "RESET";
    currentState.letter = "";
    currentState.color = "#000000";
    currentState.colorName = "";
    
    if (doc.containsKey("settings")) {
      currentState.settings = doc["settings"];
      
      // Leggi impostazioni ESP32 dal campo settings
      bool blinkAlert = false;
      int blinkCount = 3;
      int blinkDuration = 200;
      
      if (doc["settings"].containsKey("esp32")) {
        JsonObject esp32Settings = doc["settings"]["esp32"];
        blinkAlert = esp32Settings["blinkAlert"] | false;
        blinkCount = esp32Settings["blinkCount"] | 3;
        blinkDuration = esp32Settings["blinkDuration"] | 200;
      }
      
      if (blinkAlert) {
        Serial.println("💡 Eseguo lampeggio di alert");
        blinkAllLeds(blinkCount, blinkDuration);
      }
    }
    
    // Spegni tutti i LED
    turnOffAllLeds();
    ledTimerActive = false;
    currentActiveLed = -1;
    
    currentState.lastUpdate = millis();
    
    server.send(200, "application/json", "{\"status\":\"ok\",\"message\":\"Reset completed\"}");
    Serial.println("✅ Reset completato");
    return;
  }
  
  // Gestione comandi lettera
  if (doc.containsKey("letter") && doc.containsKey("color")) {
    currentState.letter = doc["letter"].as<String>();
    currentState.color = doc["color"].as<String>();
    currentState.colorName = doc.containsKey("colorName") ? doc["colorName"].as<String>() : "";
    currentState.command = "";
    
    if (doc.containsKey("settings")) {
      currentState.settings = doc["settings"];
    }
    
    currentState.lastUpdate = millis();
    
    Serial.print("📝 Lettera: ");
    Serial.print(currentState.letter);
    Serial.print(" - Colore: ");
    Serial.println(currentState.colorName);
    
    // Controlla display/LED
    displayLetter(currentState.letter, currentState.color);
    
    server.send(200, "application/json", "{\"status\":\"ok\",\"letter\":\"" + currentState.letter + "\"}");
    Serial.println("✅ Comando elaborato con successo");
    return;
  }
  
  // Comando non riconosciuto
  server.send(400, "application/json", "{\"error\":\"Invalid command format\"}");
  Serial.println("❌ Formato comando non valido");
}

// Invia stato corrente a watch (GET /receive)
void handleReceive() {
  JsonDocument doc;
  
  if (currentState.command == "RESET") {
    doc["command"] = "RESET";
    if (!currentState.settings.isNull()) {
      doc["settings"] = currentState.settings;
    }
  } else if (!currentState.letter.isEmpty()) {
    doc["letter"] = currentState.letter;
    doc["color"] = currentState.color;
    if (!currentState.colorName.isEmpty()) {
      doc["colorName"] = currentState.colorName;
    }
    if (!currentState.settings.isNull()) {
      doc["settings"] = currentState.settings;
    }
  }
  
  doc["timestamp"] = currentState.lastUpdate;
  
  String response;
  serializeJson(doc, response);
  
  server.send(200, "application/json", response);
  
  // Log solo se non è una polling request vuota
  if (!currentState.letter.isEmpty() || currentState.command == "RESET") {
    Serial.println("📤 Stato inviato a watch");
  }
}

// Status per debug (GET /status)
void handleStatus() {
  String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
  html += "<title>ESP32 Segna Server</title>";
  html += "<style>body{font-family:Arial,sans-serif;margin:20px;background:#f0f0f0;}";
  html += ".container{max-width:600px;margin:0 auto;background:white;padding:20px;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,0.1);}";
  html += "h1{color:#333;border-bottom:2px solid #4CAF50;padding-bottom:10px;}";
  html += ".info{margin:10px 0;padding:10px;background:#f9f9f9;border-left:4px solid #4CAF50;}";
  html += ".label{font-weight:bold;color:#555;}";
  html += ".value{color:#333;margin-left:10px;}";
  html += ".status{display:inline-block;padding:5px 10px;border-radius:5px;margin-left:10px;}";
  html += ".online{background:#4CAF50;color:white;}";
  html += ".offline{background:#f44336;color:white;}</style></head><body>";
  html += "<div class='container'>";
  html += "<h1>🎯 ESP32 Segna Server</h1>";
  
  html += "<div class='info'><span class='label'>📡 IP Address:</span>";
  html += "<span class='value'>" + WiFi.localIP().toString() + "</span></div>";
  
  html += "<div class='info'><span class='label'>📶 WiFi Status:</span>";
  if (WiFi.status() == WL_CONNECTED) {
    html += "<span class='status online'>Connected</span>";
    html += "<br><span class='label'>Signal:</span><span class='value'>" + String(WiFi.RSSI()) + " dBm</span>";
  } else {
    html += "<span class='status offline'>Disconnected</span>";
  }
  html += "</div>";
  
  html += "<div class='info'><span class='label'>📝 Ultima Lettera:</span>";
  html += "<span class='value'>" + (currentState.letter.isEmpty() ? "Nessuna" : currentState.letter) + "</span></div>";
  
  html += "<div class='info'><span class='label'>🎨 Colore:</span>";
  html += "<span class='value'>" + currentState.color;
  if (!currentState.colorName.isEmpty()) {
    html += " (" + currentState.colorName + ")";
  }
  html += "</span></div>";
  
  html += "<div class='info'><span class='label'>🕐 Last Update:</span>";
  html += "<span class='value'>" + String(currentState.lastUpdate) + " ms</span></div>";
  
  html += "<div class='info'><span class='label'>💾 Free Heap:</span>";
  html += "<span class='value'>" + String(ESP.getFreeHeap()) + " bytes</span></div>";
  
  html += "<div class='info'><span class='label'>⏱ Uptime:</span>";
  html += "<span class='value'>" + String(millis() / 1000) + " seconds</span></div>";
  
  html += "</div></body></html>";
  
  server.send(200, "text/html", html);
}

void displayLetter(String letter, String color) {
  // Leggi impostazioni ESP32
  bool alwaysOn = true;
  int duration = 3000;
  
  if (!currentState.settings.isNull() && currentState.settings.containsKey("esp32")) {
    JsonObject esp32Settings = currentState.settings["esp32"];
    alwaysOn = esp32Settings["alwaysOn"] | true;
    duration = esp32Settings["duration"] | 3000;
  }
  
  // Spegni LED precedente
  turnOffAllLeds();
  
  // Accendi il LED corretto
  int ledPin = -1;
  int ledIndex = -1;
  
  if (letter == "A") {
    ledPin = LED_WHITE_PIN;
    ledIndex = 0;
  } else if (letter == "B") {
    ledPin = LED_YELLOW_PIN;
    ledIndex = 1;
  } else if (letter == "C") {
    ledPin = LED_GREEN_PIN;
    ledIndex = 2;
  } else if (letter == "D") {
    ledPin = LED_RED_PIN;
    ledIndex = 3;
  } else if (letter == "E") {
    ledPin = LED_BLUE_PIN;
    ledIndex = 4;
  }
  
  if (ledPin != -1) {
    digitalWrite(ledPin, HIGH);
    currentActiveLed = ledIndex;
    Serial.print("💡 LED acceso su GPIO ");
    Serial.println(ledPin);
    
    if (!alwaysOn) {
      // Imposta timer per spegnimento
      ledStartTime = millis();
      ledDurationMs = duration;
      ledTimerActive = true;
      Serial.print("⏲ LED si spegnerà dopo ");
      Serial.print(duration);
      Serial.println(" ms");
    } else {
      ledTimerActive = false;
      Serial.println("🔒 LED rimane acceso fino a reset");
    }
  }
}

void turnOffAllLeds() {
  digitalWrite(LED_WHITE_PIN, LOW);
  digitalWrite(LED_YELLOW_PIN, LOW);
  digitalWrite(LED_GREEN_PIN, LOW);
  digitalWrite(LED_RED_PIN, LOW);
  digitalWrite(LED_BLUE_PIN, LOW);
}

void blinkAllLeds(int count, int duration) {
  for (int i = 0; i < count; i++) {
    // Accendi tutti
    digitalWrite(LED_WHITE_PIN, HIGH);
    digitalWrite(LED_YELLOW_PIN, HIGH);
    digitalWrite(LED_GREEN_PIN, HIGH);
    digitalWrite(LED_RED_PIN, HIGH);
    digitalWrite(LED_BLUE_PIN, HIGH);
    delay(duration);
    
    // Spegni tutti
    turnOffAllLeds();
    delay(duration);
  }
}

void testLeds() {
  // Test sequenziale di tutti i LED
  digitalWrite(LED_WHITE_PIN, HIGH);
  delay(200);
  digitalWrite(LED_WHITE_PIN, LOW);
  
  digitalWrite(LED_YELLOW_PIN, HIGH);
  delay(200);
  digitalWrite(LED_YELLOW_PIN, LOW);
  
  digitalWrite(LED_GREEN_PIN, HIGH);
  delay(200);
  digitalWrite(LED_GREEN_PIN, LOW);
  
  digitalWrite(LED_RED_PIN, HIGH);
  delay(200);
  digitalWrite(LED_RED_PIN, LOW);
  
  digitalWrite(LED_BLUE_PIN, HIGH);
  delay(200);
  digitalWrite(LED_BLUE_PIN, LOW);
  
  Serial.println("✅ Test LED completato");
}
