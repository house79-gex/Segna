import 'dart:convert';
import 'package:http/http.dart' as http;

class WatchWiFiService {
  String? watchIp;
  bool isConnected = false;
  
  Future<bool> connect(String ip) async {
    try {
      print('🔌 Tentativo connessione a Watch: $ip');
      
      final response = await http
          .get(Uri.parse('http://$ip:5000/status'))
          .timeout(Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        watchIp = ip;
        isConnected = true;
        print('✅ Connesso a Watch: $ip:5000');
        return true;
      }
    } catch (e) {
      print('❌ Errore connessione Watch: $e');
    }
    
    isConnected = false;
    return false;
  }
  
  void disconnect() {
    watchIp = null;
    isConnected = false;
    print('🔌 Disconnesso da Watch');
  }
  
  Future<bool> sendLetter(String letter, String colorHex, String colorName, Map<String, dynamic> settings) async {
    if (!isConnected || watchIp == null) {
      print('⚠️ Watch non connesso');
      return false;
    }
    
    try {
      final payload = {
        'letter': letter,
        'color': colorHex,
        'colorName': colorName,
        'settings': settings,
      };
      
      final response = await http
          .post(
            Uri.parse('http://$watchIp:5000/command'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(payload),
          )
          .timeout(Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ Comando inviato a Watch: $letter');
        return true;
      } else {
        print('❌ Watch risposta: ${response.statusCode}');
        return false;
      }
    } catch (e) {
      print('❌ Errore invio Watch: $e');
      return false;
    }
  }
  
  Future<bool> sendReset(Map<String, dynamic> settings) async {
    if (!isConnected || watchIp == null) {
      return false;
    }
    
    try {
      final payload = {
        'command': 'RESET',
        'settings': settings,
      };
      
      final response = await http
          .post(
            Uri.parse('http://$watchIp:5000/command'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(payload),
          )
          .timeout(Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        print('✅ Reset inviato a Watch');
        return true;
      }
      return false;
    } catch (e) {
      print('❌ Errore reset Watch: $e');
      return false;
    }
  }
}
