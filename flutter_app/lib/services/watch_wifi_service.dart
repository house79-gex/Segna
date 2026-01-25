import 'dart:convert';
import 'package:http/http.dart' as http;

/// Service per comunicazione WiFi con Watch
/// Invia comandi direttamente al Watch via HTTP POST
/// 
/// Example usage:
/// ```dart
/// final watchService = WatchWiFiService();
/// 
/// // Connect to watch
/// final connected = await watchService.connect('192.168.0.124');
/// if (connected) {
///   // Send command
///   await watchService.sendCommand('A', '#FFFFFF', 'WHITE', settings);
/// }
/// 
/// // Disconnect when done
/// watchService.disconnect();
/// ```
/// 
/// Error handling:
/// - All methods return bool indicating success/failure
/// - Errors are logged to console with descriptive messages
/// - Network timeouts are set to 3 seconds for responsive UX
class WatchWiFiService {
  String? watchIp;
  bool isConnected = false;
  
  /// Connetti a Watch verificando raggiungibilità
  /// @param ipAddress - Indirizzo IP del Watch (es: "192.168.0.124")
  /// @return true se la connessione è riuscita
  Future<bool> connect(String ipAddress) async {
    try {
      print('🔌 Tentativo connessione a Watch: $ipAddress');
      
      final response = await http
          .get(Uri.parse('http://$ipAddress:5000/status'))
          .timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        watchIp = ipAddress;
        isConnected = true;
        print('✅ Connesso a Watch: $ipAddress');
        return true;
      } else {
        print('❌ Watch risponde con status code: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Errore connessione a Watch: $e');
    }
    
    isConnected = false;
    watchIp = null;
    return false;
  }
  
  /// Invia comando al Watch
  /// @param letter - Lettera da visualizzare (A-E)
  /// @param color - Colore in formato hex (es: "#FF0000")
  /// @param colorName - Nome colore (opzionale, per debug)
  /// @param settings - Impostazioni complete del sistema
  /// @return true se l'invio è riuscito
  Future<bool> sendCommand(
    String letter, 
    String color, 
    String colorName,
    Map<String, dynamic> settings
  ) async {
    if (!isConnected || watchIp == null) {
      print('❌ Non connesso a Watch');
      return false;
    }
    
    try {
      final data = {
        'letter': letter,
        'color': color,
        'colorName': colorName,
        'settings': settings,
      };
      
      print('📤 Invio comando a Watch: $letter ($colorName)');
      
      final response = await http.post(
        Uri.parse('http://$watchIp:5000/command'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(data),
      ).timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ Comando inviato a Watch: $letter');
        return true;
      } else {
        print('❌ Watch risponde con errore: ${response.statusCode}');
        print('   Body: ${response.body}');
      }
    } catch (e) {
      print('❌ Errore invio comando a Watch: $e');
    }
    
    return false;
  }
  
  /// Invia comando RESET al Watch
  /// @param settings - Impostazioni complete del sistema
  /// @return true se l'invio è riuscito
  Future<bool> sendReset(Map<String, dynamic> settings) async {
    if (!isConnected || watchIp == null) {
      print('❌ Non connesso a Watch');
      return false;
    }
    
    try {
      final data = {
        'command': 'RESET',
        'settings': settings,
      };
      
      print('🔄 Invio comando RESET a Watch');
      
      final response = await http.post(
        Uri.parse('http://$watchIp:5000/command'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(data),
      ).timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ RESET inviato a Watch');
        return true;
      } else {
        print('❌ Watch risponde con errore: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Errore invio reset a Watch: $e');
    }
    
    return false;
  }
  
  /// Verifica se Watch è ancora raggiungibile
  /// @return true se Watch risponde
  Future<bool> checkConnection() async {
    if (watchIp == null) return false;
    
    try {
      final response = await http
          .get(Uri.parse('http://$watchIp:5000/status'))
          .timeout(const Duration(seconds: 2));
      
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }
  
  /// Disconnetti da Watch
  void disconnect() {
    isConnected = false;
    watchIp = null;
    print('🔌 Disconnesso da Watch');
  }
  
  /// Ottieni lo stato corrente di connessione
  String getConnectionStatus() {
    if (isConnected && watchIp != null) {
      return 'Connesso a $watchIp';
    } else {
      return 'Non connesso';
    }
  }
}
