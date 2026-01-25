import 'package:shared_preferences/shared_preferences.dart';

class SettingsModel {
  // Smartwatch settings
  bool vibrationModeEnabled;
  int vibrationDuration;
  int vibrationPause;
  bool resetVibrationEnabled;
  int resetVibrationDuration;
  String vibrationPattern; // numeric, morse, intensity, melodic

  // ESP32 settings
  bool ledAlwaysOn;
  int ledDuration;
  bool blinkAlertEnabled;
  int blinkCount;
  int blinkDuration;

  SettingsModel({
    this.vibrationModeEnabled = false,
    this.vibrationDuration = 300,
    this.vibrationPause = 200,
    this.resetVibrationEnabled = true,
    this.resetVibrationDuration = 800,
    this.vibrationPattern = 'numeric',
    this.ledAlwaysOn = true,
    this.ledDuration = 3000,
    this.blinkAlertEnabled = true,
    this.blinkCount = 3,
    this.blinkDuration = 200,
  });

  // Carica le impostazioni da SharedPreferences
  static Future<SettingsModel> load() async {
    final prefs = await SharedPreferences.getInstance();
    return SettingsModel(
      vibrationModeEnabled: prefs.getBool('vibrationModeEnabled') ?? false,
      vibrationDuration: prefs.getInt('vibrationDuration') ?? 300,
      vibrationPause: prefs.getInt('vibrationPause') ?? 200,
      resetVibrationEnabled: prefs.getBool('resetVibrationEnabled') ?? true,
      resetVibrationDuration: prefs.getInt('resetVibrationDuration') ?? 800,
      vibrationPattern: prefs.getString('vibrationPattern') ?? 'numeric',
      ledAlwaysOn: prefs.getBool('ledAlwaysOn') ?? true,
      ledDuration: prefs.getInt('ledDuration') ?? 3000,
      blinkAlertEnabled: prefs.getBool('blinkAlertEnabled') ?? true,
      blinkCount: prefs.getInt('blinkCount') ?? 3,
      blinkDuration: prefs.getInt('blinkDuration') ?? 200,
    );
  }

  // Salva le impostazioni in SharedPreferences
  Future<void> save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('vibrationModeEnabled', vibrationModeEnabled);
    await prefs.setInt('vibrationDuration', vibrationDuration);
    await prefs.setInt('vibrationPause', vibrationPause);
    await prefs.setBool('resetVibrationEnabled', resetVibrationEnabled);
    await prefs.setInt('resetVibrationDuration', resetVibrationDuration);
    await prefs.setString('vibrationPattern', vibrationPattern);
    await prefs.setBool('ledAlwaysOn', ledAlwaysOn);
    await prefs.setInt('ledDuration', ledDuration);
    await prefs.setBool('blinkAlertEnabled', blinkAlertEnabled);
    await prefs.setInt('blinkCount', blinkCount);
    await prefs.setInt('blinkDuration', blinkDuration);
  }

  // Converte le impostazioni in formato JSON per il payload BLE
  Map<String, dynamic> toJson() {
    return {
      'watch': {
        'vibrationMode': vibrationModeEnabled,
        'vibrationDuration': vibrationDuration,
        'vibrationPause': vibrationPause,
        'vibrationPattern': vibrationPattern,
      },
      'esp32': {
        'alwaysOn': ledAlwaysOn,
        'duration': ledDuration,
        'blinkAlert': blinkAlertEnabled,
        'blinkCount': blinkCount,
        'blinkDuration': blinkDuration,
      },
    };
  }

  // Converte le impostazioni per il comando RESET
  Map<String, dynamic> toResetJson() {
    return {
      'watch': {
        'vibrationEnabled': resetVibrationEnabled,
        'vibrationDuration': resetVibrationDuration,
      },
      'esp32': {
        'blinkAlert': blinkAlertEnabled,
        'blinkCount': blinkCount,
        'blinkDuration': blinkDuration,
      },
    };
  }
}
