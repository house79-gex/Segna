#ifndef CONFIG_H
#define CONFIG_H

// Configurazione WiFi
#define WIFI_SSID "YOUR_WIFI_SSID"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

// Configurazione Server
#define HTTP_PORT 80
#define WEBSOCKET_PORT 81

// Pin GPIO per LED singoli (mantenuti dal progetto originale)
#define LED_WHITE_PIN   25  // Lettera A - Bianco
#define LED_YELLOW_PIN  26  // Lettera B - Giallo
#define LED_GREEN_PIN   27  // Lettera C - Verde
#define LED_RED_PIN     32  // Lettera D - Rosso
#define LED_BLUE_PIN    33  // Lettera E - Blu

#endif
