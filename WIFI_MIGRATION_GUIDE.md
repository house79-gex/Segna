# WiFi Migration Guide - Segna Project

## 🎯 Overview

The Segna project has been migrated from Bluetooth to WiFi communication to resolve:
- ADB debugging issues when Bluetooth is active
- Unstable Wear OS Data Layer connections
- Testing and troubleshooting difficulties

## 🌐 New Architecture

```
┌─────────────┐                    ┌─────────────┐                    ┌─────────────┐
│  Smartphone │──── WiFi/HTTP ────→│    ESP32    │←──── WiFi/HTTP ────│    Watch    │
│ Flutter App │                    │ HTTP Server │                    │  Wear OS    │
└─────────────┘                    └─────────────┘                    └─────────────┘
```

### Data Flow:
1. **Smartphone** → POST HTTP → ESP32 (sends letter + color + settings)
2. **ESP32** → Stores last state + controls display/LED
3. **Watch** → GET/Polling → ESP32 (receives updates every 500ms)

---

## 📦 Components

### 1️⃣ ESP32 WiFi Server
**Location:** `esp32_code/esp32_wifi_server/`

**Features:**
- HTTP server on port 80
- Three endpoints: `/send`, `/receive`, `/status`
- Maintains current state (letter, color, settings)
- Controls 5 individual LEDs
- Timer-based LED auto-off
- CORS enabled for web clients

**Setup:**
1. Open `esp32_code/esp32_wifi_server/config.h`
2. Set your WiFi credentials:
   ```cpp
   #define WIFI_SSID "YOUR_WIFI_SSID"
   #define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
   ```
3. Upload to ESP32 via Arduino IDE
4. Open Serial Monitor to see assigned IP address

**Testing:**
```bash
# Check status
curl http://192.168.0.100/status

# Send letter A (red)
curl -X POST http://192.168.0.100/send \
  -H "Content-Type: application/json" \
  -d '{"letter":"A","color":"#FFFFFF","colorName":"WHITE","settings":{"esp32":{"alwaysOn":true}}}'

# Get current state
curl http://192.168.0.100/receive
```

### 2️⃣ Flutter App
**Location:** `flutter_app/`

**Changes:**
- Removed: `flutter_blue_plus`, `permission_handler` dependencies
- Added: `http` package for WiFi communication
- New service: `lib/services/wifi_communication_service.dart`
- Updated UI: IP address input field + Connect/Disconnect button

**Usage:**
1. Launch app
2. Enter ESP32 IP address (e.g., `192.168.0.100`)
3. Tap "Connetti ESP32"
4. Send letters A-E to ESP32
5. Watch receives updates automatically

**Installation:**
```bash
cd flutter_app
flutter pub get
flutter run
```

### 3️⃣ Wear OS Watch App
**Location:** `wear_os_app/`

**Changes:**
- New class: `WiFiReceiver.kt` (replaces MessageClient)
- HTTP polling every 500ms to ESP32
- Settings now include ESP32 IP configuration
- Internet permissions added to AndroidManifest.xml

**Setup:**
1. Install app on watch
2. Open Settings (gear icon)
3. Enter ESP32 IP address at the top
4. Tap "Salva"
5. Watch will auto-connect and start polling

**Build:**
```bash
cd wear_os_app
./gradlew assembleRelease
# APK will be in app/build/outputs/apk/release/
```

---

## 🚀 Quick Start Guide

### Prerequisites
- ESP32 board with 5 LEDs connected
- WiFi network (all devices must be on same network)
- Android smartphone with Flutter app
- Wear OS smartwatch

### Step-by-Step Setup

#### 1. Configure ESP32
```bash
1. Edit esp32_code/esp32_wifi_server/config.h
2. Set WIFI_SSID and WIFI_PASSWORD
3. Upload sketch to ESP32
4. Open Serial Monitor (115200 baud)
5. Note the IP address (e.g., 192.168.0.100)
```

#### 2. Configure Flutter App
```bash
1. Install Flutter app on smartphone
2. Launch app
3. Enter ESP32 IP address
4. Tap "Connetti ESP32"
5. Verify green status indicator
```

#### 3. Configure Watch
```bash
1. Install Wear OS app on watch
2. Open app
3. Tap Settings icon (top right)
4. Scroll to top and enter ESP32 IP
5. Tap "Salva"
6. App will auto-connect
```

#### 4. Test System
```bash
1. On smartphone, tap letter "A" (white)
2. ESP32 LED should light up white
3. Watch should display white screen with "A"
4. Tap "Reset" button to clear all displays
```

---

## 🔧 Troubleshooting

### ESP32 Not Connecting to WiFi
- Check SSID/password in `config.h`
- Verify 2.4GHz WiFi (ESP32 doesn't support 5GHz)
- Check Serial Monitor for error messages

### Flutter App Can't Connect
- Verify ESP32 IP is correct
- Ping ESP32: `ping 192.168.0.100`
- Check firewall settings
- Ensure devices on same network

### Watch Not Receiving Updates
- Open watch Settings and verify IP address
- Check ESP32 is responding: `curl http://IP/status`
- Restart watch app
- Check watch has internet connectivity

### LED Not Lighting
- Verify GPIO pin connections in `config.h`
- Check LED wiring and polarity
- Test LEDs with `/status` endpoint
- Monitor Serial output for errors

---

## 📊 API Reference

### ESP32 Endpoints

#### POST /send
Receive command from smartphone.

**Request:**
```json
{
  "letter": "A",
  "color": "#FFFFFF",
  "colorName": "WHITE",
  "settings": {
    "esp32": {
      "alwaysOn": true,
      "duration": 3000
    }
  }
}
```

**Response:**
```json
{
  "status": "ok",
  "letter": "A"
}
```

#### GET /receive
Get current state for watch.

**Response:**
```json
{
  "letter": "A",
  "color": "#FFFFFF",
  "colorName": "WHITE",
  "timestamp": 1234567890,
  "settings": {...}
}
```

#### GET /status
Web-based status page (open in browser).

---

## 🎨 LED Mapping

| Letter | Color  | GPIO Pin | Hex Color |
|--------|--------|----------|-----------|
| A      | White  | GPIO 25  | #FFFFFF   |
| B      | Yellow | GPIO 26  | #FFFF00   |
| C      | Green  | GPIO 27  | #00FF00   |
| D      | Red    | GPIO 32  | #FF0000   |
| E      | Blue   | GPIO 33  | #0000FF   |

---

## ⚙️ Configuration Options

### ESP32 Settings (in JSON payload)
```json
{
  "esp32": {
    "alwaysOn": true,        // Keep LED on until reset
    "duration": 3000,        // LED duration in ms (if alwaysOn=false)
    "blinkAlert": true,      // Blink all LEDs on reset
    "blinkCount": 3,         // Number of blinks
    "blinkDuration": 200     // Blink duration in ms
  }
}
```

### Watch Settings (in JSON payload)
```json
{
  "watch": {
    "vibrationMode": false,      // Use vibration instead of display
    "vibrationEnabled": true,    // Enable vibration on reset
    "vibrationDuration": 300,    // Vibration duration in ms
    "vibrationPause": 200        // Pause between vibrations
  }
}
```

---

## 📈 Performance

| Metric | Value |
|--------|-------|
| Latency (Smartphone → ESP32) | < 100ms |
| Latency (ESP32 → Watch) | < 1000ms (polling) |
| Polling Interval | 500ms |
| Max Connection Range | ~30m (WiFi dependent) |
| Power Consumption | Higher than BLE |

---

## 🔒 Security Considerations

⚠️ **Current Implementation:**
- No authentication
- No encryption (HTTP not HTTPS)
- Open network access

🛡️ **Production Recommendations:**
1. Add authentication token/password
2. Implement HTTPS (TLS/SSL)
3. Use WPA2/WPA3 encrypted WiFi
4. Implement MAC address filtering
5. Rate limiting on endpoints

---

## 📝 Migration Benefits

✅ **Advantages:**
- Debug always active (ADB works with WiFi)
- Faster response (< 100ms vs ~300ms BLE)
- More stable connections
- Greater range (30m vs 10m)
- Easily testable (curl, Postman, browser)
- Scalable (multiple devices possible)

❌ **Trade-offs:**
- Requires WiFi network
- Slightly higher power consumption
- All devices must be on same network
- No direct device-to-device pairing

---

## 🐛 Known Issues

1. **Watch polling may drain battery faster** → Consider increasing polling interval
2. **Network changes require reconfiguration** → Add network discovery feature
3. **No offline mode** → BLE fallback could be added
4. **HTTP timeout errors if ESP32 unreachable** → Implement retry logic

---

## 🔮 Future Enhancements

- [ ] WebSocket support for real-time bidirectional communication
- [ ] mDNS/Bonjour for automatic ESP32 discovery
- [ ] Authentication and encryption (HTTPS)
- [ ] Mobile hotspot mode (ESP32 as AP)
- [ ] OTA firmware updates
- [ ] Network status monitoring
- [ ] Offline mode with BLE fallback

---

## 📞 Support

For issues or questions:
1. Check Serial Monitor output (ESP32)
2. Check Logcat output (Android/Wear OS)
3. Test endpoints with curl/Postman
4. Review this guide's troubleshooting section

---

## 📜 License

This project follows the same license as the main Segna repository.

---

**Last Updated:** 2026-01-24
**Version:** 1.0.0
