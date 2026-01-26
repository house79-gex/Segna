import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

class AndroidReceiverService {
  String? androidIp;
  bool isConnected = false;
  final int port = 5001;

  /// Connette all'Android Receiver via HTTP
  Future<bool> connect(String ip) async {
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        print('🔌 Tentativo $attempt/2 connessione Android Receiver: $ip');
        
        final response = await http
            .get(Uri.parse('http://$ip:$port/status'))
            .timeout(Duration(seconds: 10));
        
        if (response.statusCode == 200) {
          androidIp = ip;
          isConnected = true;
          print('✅ Connesso ad Android Receiver: $ip:$port');
          return true;
        }
      } on TimeoutException catch (e) {
        print('❌ Timeout tentativo $attempt: $e');
      } on SocketException catch (e) {
        print('❌ Socket error tentativo $attempt: $e');
      } catch (e) {
        print('❌ Tentativo $attempt fallito: $e');
      }
      
      if (attempt < 2) {
        await Future.delayed(Duration(seconds: 2));
      }
    }
    
    isConnected = false;
    print('❌ Connessione Android Receiver fallita dopo 2 tentativi');
    return false;
  }

  /// Invia comando JSON all'Android Receiver
  Future<bool> sendCommand(Map<String, dynamic> commandData) async {
    if (!isConnected || androidIp == null) {
      print('❌ Android Receiver non connesso');
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse('http://$androidIp:$port/command'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(commandData),
      ).timeout(Duration(seconds: 5));

      if (response.statusCode == 200) {
        final command = commandData['letter'] ?? commandData['command'] ?? 'unknown';
        print('✅ Comando inviato ad Android Receiver: $command');
        return true;
      } else {
        print('⚠️ Android Receiver risposta: ${response.statusCode}');
        return false;
      }
    } on TimeoutException catch (e) {
      print('❌ Timeout invio comando ad Android Receiver: $e');
    } on SocketException catch (e) {
      print('❌ Socket error Android Receiver: $e');
    } catch (e) {
      print('❌ Errore invio comando ad Android Receiver: $e');
    }
    return false;
  }

  /// Disconnette dall'Android Receiver
  void disconnect() {
    androidIp = null;
    isConnected = false;
    print('🔌 Disconnesso da Android Receiver');
  }
}
