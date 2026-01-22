import 'package:flutter/material.dart';
import 'models/settings_model.dart';

class SettingsPage extends StatefulWidget {
  final SettingsModel settings;

  const SettingsPage({super.key, required this.settings});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late SettingsModel _settings;

  @override
  void initState() {
    super.initState();
    _settings = widget.settings;
  }

  Future<void> _saveSettings() async {
    await _settings.save();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Impostazioni salvate')),
      );
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Impostazioni'),
        actions: [
          IconButton(
            icon: const Icon(Icons.save),
            onPressed: _saveSettings,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Sezione Smartwatch
          const Text(
            'Smartwatch',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            title: const Text('Modalità Vibrazione'),
            subtitle: const Text('Abilita vibrazione invece di visualizzazione'),
            value: _settings.vibrationModeEnabled,
            onChanged: (value) {
              setState(() {
                _settings.vibrationModeEnabled = value;
              });
            },
          ),
          ListTile(
            title: const Text('Durata Vibrazione (ms)'),
            subtitle: Text('${_settings.vibrationDuration} ms'),
            trailing: SizedBox(
              width: 150,
              child: Slider(
                value: _settings.vibrationDuration.toDouble(),
                min: 100,
                max: 1000,
                divisions: 18,
                label: '${_settings.vibrationDuration} ms',
                onChanged: (value) {
                  setState(() {
                    _settings.vibrationDuration = value.toInt();
                  });
                },
              ),
            ),
          ),
          ListTile(
            title: const Text('Pausa tra Vibrazioni (ms)'),
            subtitle: Text('${_settings.vibrationPause} ms'),
            trailing: SizedBox(
              width: 150,
              child: Slider(
                value: _settings.vibrationPause.toDouble(),
                min: 100,
                max: 1000,
                divisions: 18,
                label: '${_settings.vibrationPause} ms',
                onChanged: (value) {
                  setState(() {
                    _settings.vibrationPause = value.toInt();
                  });
                },
              ),
            ),
          ),
          SwitchListTile(
            title: const Text('Vibrazione Reset'),
            subtitle: const Text('Abilita vibrazione prolungata per reset'),
            value: _settings.resetVibrationEnabled,
            onChanged: (value) {
              setState(() {
                _settings.resetVibrationEnabled = value;
              });
            },
          ),
          ListTile(
            title: const Text('Durata Vibrazione Reset (ms)'),
            subtitle: Text('${_settings.resetVibrationDuration} ms'),
            trailing: SizedBox(
              width: 150,
              child: Slider(
                value: _settings.resetVibrationDuration.toDouble(),
                min: 200,
                max: 2000,
                divisions: 18,
                label: '${_settings.resetVibrationDuration} ms',
                onChanged: (value) {
                  setState(() {
                    _settings.resetVibrationDuration = value.toInt();
                  });
                },
              ),
            ),
          ),
          const Divider(height: 32),

          // Sezione ESP32
          const Text(
            'ESP32',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            title: const Text('LED Sempre Acceso'),
            subtitle: const Text('LED acceso fino a reset'),
            value: _settings.ledAlwaysOn,
            onChanged: (value) {
              setState(() {
                _settings.ledAlwaysOn = value;
              });
            },
          ),
          if (!_settings.ledAlwaysOn)
            ListTile(
              title: const Text('Durata LED (ms)'),
              subtitle: Text('${_settings.ledDuration} ms'),
              trailing: SizedBox(
                width: 150,
                child: Slider(
                  value: _settings.ledDuration.toDouble(),
                  min: 500,
                  max: 10000,
                  divisions: 19,
                  label: '${_settings.ledDuration} ms',
                  onChanged: (value) {
                    setState(() {
                      _settings.ledDuration = value.toInt();
                    });
                  },
                ),
              ),
            ),
          SwitchListTile(
            title: const Text('Lampeggio Avviso'),
            subtitle: const Text('Abilita lampeggio tutti i LED per reset'),
            value: _settings.blinkAlertEnabled,
            onChanged: (value) {
              setState(() {
                _settings.blinkAlertEnabled = value;
              });
            },
          ),
          ListTile(
            title: const Text('Numero Lampeggi'),
            subtitle: Text('${_settings.blinkCount} lampeggi'),
            trailing: SizedBox(
              width: 150,
              child: Slider(
                value: _settings.blinkCount.toDouble(),
                min: 1,
                max: 10,
                divisions: 9,
                label: '${_settings.blinkCount}',
                onChanged: (value) {
                  setState(() {
                    _settings.blinkCount = value.toInt();
                  });
                },
              ),
            ),
          ),
          ListTile(
            title: const Text('Durata Lampeggio (ms)'),
            subtitle: Text('${_settings.blinkDuration} ms'),
            trailing: SizedBox(
              width: 150,
              child: Slider(
                value: _settings.blinkDuration.toDouble(),
                min: 100,
                max: 1000,
                divisions: 18,
                label: '${_settings.blinkDuration} ms',
                onChanged: (value) {
                  setState(() {
                    _settings.blinkDuration = value.toInt();
                  });
                },
              ),
            ),
          ),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: _saveSettings,
            icon: const Icon(Icons.save),
            label: const Text('Salva Impostazioni'),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.all(16),
            ),
          ),
        ],
      ),
    );
  }
}