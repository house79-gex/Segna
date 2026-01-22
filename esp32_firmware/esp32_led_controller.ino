/*
 * ESP32 BLE LED Controller
 * Segna Project - Ricevitore BLE per controllo 5 LED singoli
 *
 * Hardware:
 * - LED Bianco: GPIO 25 (Lettera A)
 * - LED Giallo: GPIO 26 (Lettera B)
 * - LED Verde: GPIO 27 (Lettera C)
 * - LED Rosso: GPIO 32 (Lettera D)
 * - LED Blu: GPIO 33 (Lettera E)
 *
 * Dipendenze:
 * - ArduinoJson
 * - ESP32 BLE Arduino (incluso in ESP32 core)
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>

// UUIDs per il servizio BLE
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Pin GPIO per LED singoli
#define LED_WHITE_PIN   25  // Lettera A - Bianco
#define LED_YELLOW_PIN  26  // Lettera B - Giallo
#define LED_GREEN_PIN   27  // Lettera C - Verde
#define LED_RED_PIN     32  // Lettera D - Rosso
#define LED_BLUE_PIN    33  // Lettera E - Blu

// Dimensione buffer JSON
#define JSON_BUFFER_SIZE 512

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

// Variabili per gestione LED temporizzato
unsigned long ledStartTime = 0;
int ledDurationMs = 0;
bool ledTimerActive = false;
int currentActiveLed = -1; // -1 = nessun LED, 0-4 = LED attivo

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Client connesso");
    }

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Client disconnesso");
      BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue();

      if (value.length() > 0) {
        Serial.println("Ricevuto comando:");
        Serial.println(value.c_str());

        // Parse JSON
        StaticJsonDocument<JSON_BUFFER_SIZE> doc;
        DeserializationError error = deserializeJson(doc, value.c_str());

        if (error) {
          Serial.print("Errore parsing JSON: ");
          Serial.println(error.c_str());
          return;
        }

        // Gestione comando RESET
        if (doc.containsKey("command") && doc["command"] == "RESET") {
          Serial.println("Comando RESET ricevuto");

          // Leggi impostazioni ESP32 dal campo settings
          bool blinkAlert = false;
          int blinkCount = 3;
          int blinkDuration = 200;

          if (doc.containsKey("settings") && doc["settings"].containsKey("esp32")) {
            JsonObject esp32Settings = doc["settings"]["esp32"];
            blinkAlert = esp32Settings["blinkAlert"] | false;
            blinkCount = esp32Settings["blinkCount"] | 3;
            blinkDuration = esp32Settings["blinkDuration"] | 200;
          }

          if (blinkAlert) {
            // Lampeggia tutti i LED
            blinkAllLeds(blinkCount, blinkDuration);
          }

          // Spegni tutti i LED
          turnOffAllLeds();
          ledTimerActive = false;
          currentActiveLed = -1;

          // Invia conferma
          sendConfirmation("#000000");
          return;
        }

        // Gestione comandi lettera
        if (doc.containsKey("letter") && doc.containsKey("colorName")) {
          String letter = doc["letter"].as<String>();
          String colorName = doc["colorName"].as<String>();
          String colorHex = doc["color"].as<String>();

          Serial.print("Lettera: ");
          Serial.print(letter);
          Serial.print(" - Colore: ");
          Serial.println(colorName);

          // Leggi impostazioni ESP32
          bool alwaysOn = true;
          int duration = 3000;

          if (doc.containsKey("settings") && doc["settings"].containsKey("esp32")) {
            JsonObject esp32Settings = doc["settings"]["esp32"];
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
            Serial.print("LED acceso su GPIO ");
            Serial.println(ledPin);

            if (!alwaysOn) {
              // Imposta timer per spegnimento
              ledStartTime = millis();
              ledDurationMs = duration;
              ledTimerActive = true;
              Serial.print("LED si spegnerà dopo ");
              Serial.print(duration);
              Serial.println(" ms");
            } else {
              ledTimerActive = false;
              Serial.println("LED rimane acceso fino a reset");
            }
          }

          // Invia conferma
          sendConfirmation(colorHex);
        }
      }
    }
};

void turnOffAllLeds() {
  digitalWrite(LED_WHITE_PIN, LOW);
  digitalWrite(LED_YELLOW_PIN, LOW);
  digitalWrite(LED_GREEN_PIN, LOW);
  digitalWrite(LED_RED_PIN, LOW);
  digitalWrite(LED_BLUE_PIN, LOW);
  Serial.println("Tutti i LED spenti");
}

void blinkAllLeds(int count, int duration) {
  Serial.print("Lampeggio ");
  Serial.print(count);
  Serial.println(" volte");

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

void sendConfirmation(String colorHex) {
  if (deviceConnected && pCharacteristic != NULL) {
    StaticJsonDocument<128> confirmDoc;
    confirmDoc["status"] = "received";
    confirmDoc["device"] = "esp32";
    confirmDoc["color"] = colorHex;

    String confirmJson;
    serializeJson(confirmDoc, confirmJson);

    pCharacteristic->setValue(confirmJson.c_str());
    pCharacteristic->notify();

    Serial.print("Conferma inviata: ");
    Serial.println(confirmJson);
  }
}

void setup() {
  Serial.begin(115200);
  Serial.println("Avvio ESP32 BLE LED Controller...");

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
  digitalWrite(LED_WHITE_PIN, HIGH);
  delay(300);
  digitalWrite(LED_WHITE_PIN, LOW);

  digitalWrite(LED_YELLOW_PIN, HIGH);
  delay(300);
  digitalWrite(LED_YELLOW_PIN, LOW);

  digitalWrite(LED_GREEN_PIN, HIGH);
  delay(300);
  digitalWrite(LED_GREEN_PIN, LOW);

  digitalWrite(LED_RED_PIN, HIGH);
  delay(300);
  digitalWrite(LED_RED_PIN, LOW);

  digitalWrite(LED_BLUE_PIN, HIGH);
  delay(300);
  digitalWrite(LED_BLUE_PIN, LOW);

  // Inizializza BLE
  BLEDevice::init("ESP32-Segna");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Crea il servizio BLE
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Crea la caratteristica BLE con supporto NOTIFY
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pCharacteristic->addDescriptor(new BLE2902());

  // Avvia il servizio
  pService->start();

  // Avvia advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE Server pronto!");
  Serial.println("In attesa di connessioni...");
}

void loop() {
  // Gestione timer LED temporizzato
  if (ledTimerActive && (millis() - ledStartTime >= ledDurationMs)) {
    Serial.println("Timeout LED raggiunto, spegnimento...");
    turnOffAllLeds();
    ledTimerActive = false;
    currentActiveLed = -1;
  }

  // Se disconnesso, mantieni advertising attivo
  if (!deviceConnected) {
    delay(500);
  }
  delay(10);
}
