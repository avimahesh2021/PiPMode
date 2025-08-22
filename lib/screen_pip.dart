import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class ScreenPip {
  static const _ch = MethodChannel('pip_channel');

  /// Check if PiP is supported
  static Future<bool> isPiPSupported() async {
    final ok = await _ch.invokeMethod('isPiPSupported');
    return ok == true;
  }

  /// Enter normal Flutter PiP (shrinks your UI)
  static Future<void> enterPiP() async {
    try {
      await _ch.invokeMethod('enterPiP');
    } catch (e) {
      debugPrint("Error entering PiP: $e");
    }
  }

  /// Start live device screen mirror in PiP (via native MediaProjection)
  static Future<void> startLivePiP({bool withAudio = false}) async {
    try {
      await _ch.invokeMethod('startLivePiP', {'withAudio': withAudio});
    } catch (e) {
      debugPrint("Error starting Live PiP: $e");
    }
  }

  /// Stop screen mirror PiP
  static Future<void> stopLivePiP() async {
    try {
      await _ch.invokeMethod('stopLivePiP');
    } catch (e) {
      debugPrint("Error stopping Live PiP: $e");
    }
  }
}
