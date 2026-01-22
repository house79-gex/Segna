/*
 * ESP32 BLE LED Controller
 * Segna Project - Ricevitore BLE per controllo LED RGB
 *
 * Hardware:
 * - LED RGB Catodo Comune: GPIO 25 (R), GPIO 26 (G), GPIO 27 (B)
 * - LED Strip WS2812B: GPIO 23 (opzionale)
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

// Pin GPIO per LED RGB (catodo comune)
#define PIN_RED    25
#define PIN_GREEN  26
#define PIN_BLUE   27

// Configurazione PWM per LED RGB
#define PWM_FREQ     5000
#define PWM_RES_BITS 8
#define RED_CHANNEL   0
#define GREEN_CHANNEL 1
#define BLUE_CHANNEL  2

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

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
        StaticJsonDocument<256> doc;
        DeserializationError error = deserializeJson(doc, value.c_str());

        if (error) {
          Serial.print("Errore parsing JSON: ");
          Serial.println(error.c_str());
          return;
        }

        // Gestione comando RESET
        if (doc.containsKey("command") && doc["command"] == "RESET") {
          Serial.println("Comando RESET ricevuto");
          setLEDColor(0, 0, 0);
          return;
        }

        // Gestione comandi lettera
        if (doc.containsKey("letter") && doc.containsKey("colorName")) {
          String letter = doc["letter"].as<String>();
          String colorName = doc["colorName"].as<String>();

          Serial.print("Lettera: ");
          Serial.print(letter);
          Serial.print(" - Colore: ");
          Serial.println(colorName);

          // Imposta il colore del LED in base alla lettera
          if (letter == "A") {
            setLEDColor(255, 255, 255); // Bianco
          } else if (letter == "B") {
            setLEDColor(255, 255, 0);   // Giallo
          } else if (letter == "C") {
            setLEDColor(0, 255, 0);     // Verde
          } else if (letter == "D") {
            setLEDColor(255, 0, 0);     // Rosso
          } else if (letter == "E") {
            setLEDColor(0, 0, 255);     // Blu
          }
        }
      }
    }
};

void setLEDColor(int red, int green, int blue) {
  // Scrivi i valori PWM per ogni canale colore
  ledcWrite(RED_CHANNEL, red);
  ledcWrite(GREEN_CHANNEL, green);
  ledcWrite(BLUE_CHANNEL, blue);

  Serial.print("LED impostato su RGB(");
  Serial.print(red);
  Serial.print(", ");
  Serial.print(green);
  Serial.print(", ");
  Serial.print(blue);
  Serial.println(")");
}

void setup() {
  Serial.begin(115200);
  Serial.println("Avvio ESP32 BLE LED Controller...");

  // Configura pin LED come output con PWM
  ledcSetup(RED_CHANNEL, PWM_FREQ, PWM_RES_BITS);
  ledcSetup(GREEN_CHANNEL, PWM_FREQ, PWM_RES_BITS);
  ledcSetup(BLUE_CHANNEL, PWM_FREQ, PWM_RES_BITS);

  ledcAttachPin(PIN_RED, RED_CHANNEL);
  ledcAttachPin(PIN_GREEN, GREEN_CHANNEL);
  ledcAttachPin(PIN_BLUE, BLUE_CHANNEL);

  // Test LED all'avvio
  Serial.println("Test LED...");
  setLEDColor(255, 0, 0);   // Rosso
  delay(500);
  setLEDColor(0, 255, 0);   // Verde
  delay(500);
  setLEDColor(0, 0, 255);   // Blu
  delay(500);
  setLEDColor(0, 0, 0);     // Spento

  // Inizializza BLE
  BLEDevice::init("ESP32-Segna");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Crea il servizio BLE
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Crea la caratteristica BLE
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE
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
  // Se disconnesso, mantieni advertising attivo
  if (!deviceConnected) {
    delay(500);
  }
  delay(10);
}
