import 'dart:io';
import 'dart:convert';

class SmartphoneServer {
  HttpServer? _server;
  String? _lastLetter;
  String? _lastColor;
  String? _lastColorName;
  int _lastTimestamp = 0;
  
  Future<String?> start() async {
    try {
      _server = await HttpServer.bind(InternetAddress.anyIPv4, 8080);
      final localIp = _getLocalIp();
      print('📡 Server smartphone avviato su $localIp:8080');
      
      _server!.listen((HttpRequest request) {
        // CORS headers
        request.response.headers.add('Access-Control-Allow-Origin', '*');
        request.response.headers.add('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        request.response.headers.add('Access-Control-Allow-Headers', 'Content-Type');
        
        if (request.method == 'OPTIONS') {
          request.response
            ..statusCode = 200
            ..close();
          return;
        }
        
        if (request.method == 'GET' && request.uri.path == '/receive') {
          final response = jsonEncode({
            'letter': _lastLetter ?? '',
            'color': _lastColor ?? '#000000',
            'colorName': _lastColorName ?? '',
            'timestamp': _lastTimestamp,
          });
          
          request.response
            ..headers.contentType = ContentType.json
            ..statusCode = 200
            ..write(response)
            ..close();
          
          print('📤 Watch polling - Risposta: $_lastLetter');
        } else {
          request.response
            ..statusCode = 404
            ..write('Not Found')
            ..close();
        }
      });
      
      return localIp;
    } catch (e) {
      print('❌ Errore avvio server: $e');
      return null;
    }
  }
  
  void updateState(String letter, String color, String colorName) {
    _lastLetter = letter;
    _lastColor = color;
    _lastColorName = colorName;
    _lastTimestamp = DateTime.now().millisecondsSinceEpoch;
    print('📤 Stato aggiornato per watch: $letter - $colorName');
  }
  
  void reset() {
    _lastLetter = '';
    _lastColor = '#000000';
    _lastColorName = '';
    _lastTimestamp = DateTime.now().millisecondsSinceEpoch;
    print('🔄 Stato reset per watch');
  }
  
String _getLocalIp() {
  try {
    for (var interface in NetworkInterface.list(includeLoopback: false, type: InternetAddressType.IPv4)) {
      for (var addr in interface.addresses) {
        if (addr.type == InternetAddressType.IPv4 && 
            !addr.isLoopback && 
            !addr.address.startsWith('169.254')) {
          return addr.address;
        }
      }
    }
  } catch (e) {
    print('⚠️ Errore rilevamento IP: $e');
  }
  return 'unknown';
}
  
  void stop() {
    _server?.close();
    print('🛑 Server smartphone fermato');
  }
}
