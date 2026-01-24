import 'dart:convert';
import 'package:http/http.dart' as http;

/// Service per comunicazione WiFi con ESP32
/// Sostituisce completamente la comunicazione Bluetooth
class WiFiCommunicationService {
  String? esp32Ip;
  bool isConnected = false;
  
  /// Connetti a ESP32 verificando raggiungibilità
  /// @param ipAddress - Indirizzo IP dell'ESP32 (es: "192.168.0.100")
  /// @return true se la connessione è riuscita
  Future<bool> connect(String ipAddress) async {
    try {
      print('🔌 Tentativo connessione a ESP32: $ipAddress');
      
      final response = await http
          .get(Uri.parse('http://$ipAddress/status'))
          .timeout(const Duration(seconds: 5));
      
      if (response.statusCode == 200) {
        esp32Ip = ipAddress;
        isConnected = true;
        print('✅ Connesso a ESP32: $ipAddress');
        return true;
      } else {
        print('❌ ESP32 risponde con status code: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Errore connessione a ESP32: $e');
    }
    
    isConnected = false;
    esp32Ip = null;
    return false;
  }
  
  /// Invia lettera + colore + impostazioni a ESP32
  /// @param letter - Lettera da visualizzare (A-E)
  /// @param color - Colore in formato hex (es: "#FF0000")
  /// @param colorName - Nome colore (opzionale, per debug)
  /// @param settings - Impostazioni complete del sistema
  /// @return true se l'invio è riuscito
  Future<bool> sendLetter(
    String letter, 
    String color, 
    String colorName,
    Map<String, dynamic> settings
  ) async {
    if (!isConnected || esp32Ip == null) {
      print('❌ Non connesso a ESP32');
      return false;
    }
    
    try {
      final data = {
        'letter': letter,
        'color': color,
        'colorName': colorName,
        'settings': settings,
      };
      
      print('📤 Invio lettera a ESP32: $letter ($colorName)');
      
      final response = await http.post(
        Uri.parse('http://$esp32Ip/send'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(data),
      ).timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ Lettera inviata con successo: $letter');
        return true;
      } else {
        print('❌ ESP32 risponde con errore: ${response.statusCode}');
        print('   Body: ${response.body}');
      }
    } catch (e) {
      print('❌ Errore invio lettera: $e');
    }
    
    return false;
  }
  
  /// Invia comando RESET a ESP32
  /// @param settings - Impostazioni complete del sistema
  /// @return true se l'invio è riuscito
  Future<bool> sendReset(Map<String, dynamic> settings) async {
    if (!isConnected || esp32Ip == null) {
      print('❌ Non connesso a ESP32');
      return false;
    }
    
    try {
      final data = {
        'command': 'RESET',
        'settings': settings,
      };
      
      print('🔄 Invio comando RESET a ESP32');
      
      final response = await http.post(
        Uri.parse('http://$esp32Ip/send'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(data),
      ).timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ RESET inviato con successo');
        return true;
      } else {
        print('❌ ESP32 risponde con errore: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Errore invio reset: $e');
    }
    
    return false;
  }
  
  /// Verifica se ESP32 è ancora raggiungibile
  /// @return true se ESP32 risponde
  Future<bool> checkConnection() async {
    if (esp32Ip == null) return false;
    
    try {
      final response = await http
          .get(Uri.parse('http://$esp32Ip/status'))
          .timeout(const Duration(seconds: 2));
      
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }
  
  /// Disconnetti da ESP32
  void disconnect() {
    isConnected = false;
    esp32Ip = null;
    print('🔌 Disconnesso da ESP32');
  }
  
  /// Ottieni lo stato corrente di connessione
  String getConnectionStatus() {
    if (isConnected && esp32Ip != null) {
      return 'Connesso a $esp32Ip';
    } else {
      return 'Non connesso';
    }
  }
}
