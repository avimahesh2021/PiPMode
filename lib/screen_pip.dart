import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class ScreenPip {
  static const _ch = MethodChannel('pip_channel');



  /// Check if PiP is supported
  static Future<bool> isPiPSupported() async {
    try {
      final ok = await _ch.invokeMethod('isPiPSupported');
      debugPrint("🔍 Flutter: isPiPSupported result: $ok");
      return ok == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error checking PiP support: $e");
      return false;
    }
  }

  /// Check if overlay permission is granted
  static Future<bool> checkOverlayPermission() async {
    try {
      final result = await _ch.invokeMethod('checkOverlayPermission');
      debugPrint("🔍 Flutter: checkOverlayPermission result: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error checking overlay permission: $e");
      return false;
    }
  }

  /// Check if PiP permission is granted for this app specifically
  static Future<bool> checkPiPPermission() async {
    try {
      final result = await _ch.invokeMethod('checkPiPPermission');
      debugPrint("🔍 Flutter: checkPiPPermission result: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error checking PiP permission: $e");
      return false;
    }
  }

  /// Request overlay permission
  static Future<bool> requestOverlayPermission() async {
    try {
      final result = await _ch.invokeMethod('requestOverlayPermission');
      debugPrint("🔍 Flutter: requestOverlayPermission result: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error requesting overlay permission: $e");
      return false;
    }
  }

  /// Start live device screen mirror in PiP
  static Future<bool> startLivePiP({bool withAudio = false}) async {
    try {
      debugPrint("🔍 Flutter: Requesting screen capture permission...");
      final result = await _ch.invokeMethod('startLivePiP', {'withAudio': withAudio});
      debugPrint("✅ Flutter: Screen capture permission granted and PiP started: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error starting Live PiP: $e");
      String message = "An unexpected error occurred while starting PiP";
      String userAction = "Please try again";
      
      if (e is PlatformException) {
        switch (e.code) {
          case 'PERMISSION_DENIED':
            message = "Screen capture permission was denied";
            userAction = "Grant permission and select 'Share entire screen' for best results";
            break;
          case 'PERMISSION_ERROR':
            message = "Overlay permission is required for global PiP access";
            userAction = "Please enable overlay permission in system settings";
            break;
          case 'CAPTURE_ERROR':
            message = "Failed to initialize screen capture";
            userAction = "Check if other apps are using screen capture, then try again";
            break;
          case 'SETUP_ERROR':
            message = "Screen capture setup failed: ${e.message ?? 'Unknown setup issue'}";
            userAction = "Restart the app and ensure you have sufficient device resources";
            break;
          case 'DATA_VALIDATION_FAILED':
            message = "Screen capture data is invalid";
            userAction = "Select 'Share entire screen' instead of 'Share one app' when prompted";
            break;
          case 'SERVICE_ERROR':
            message = "Background service failed to start";
            userAction = "Check device storage and memory, then restart the app";
            break;
          case 'SURFACE_ERROR':
            message = "Display surface creation failed";
            userAction = "Device may not support this feature or resources are low";
            break;
          default:
            message = "PiP startup failed: ${e.message ?? 'Unknown platform error'}";
            userAction = "Check device compatibility and try restarting the app";
        }
      } else {
        message = "Unexpected error: ${e.toString()}";
        userAction = "Please report this issue if it persists";
      }
      
      throw PlatformException(
        code: e is PlatformException ? e.code : 'UNKNOWN_ERROR',
        message: "$message. $userAction",
        details: {
          'original_error': e.toString(),
          'user_action': userAction,
          'timestamp': DateTime.now().toIso8601String(),
        },
      );
    }
  }

  /// Stop screen mirror PiP
  static Future<void> stopLivePiP() async {
    try {
      await _ch.invokeMethod('stopLivePiP');
      debugPrint("✅ Flutter: Live PiP stopped");
    } catch (e) {
      debugPrint("❌ Flutter: Error stopping Live PiP: $e");
      
      String message = "Failed to stop PiP broadcasting";
      String userAction = "PiP may still be running in background";
      
      if (e is PlatformException) {
        switch (e.code) {
          case 'SERVICE_NOT_RUNNING':
            message = "PiP service was not running";
            userAction = "No action needed - PiP is already stopped";
            break;
          case 'CLEANUP_ERROR':
            message = "Error occurred during PiP cleanup";
            userAction = "PiP should stop automatically, restart app if issues persist";
            break;
          default:
            message = "Failed to stop PiP: ${e.message ?? 'Unknown error'}";
            userAction = "Try restarting the app to ensure complete cleanup";
        }
      }
      
      throw PlatformException(
        code: e is PlatformException ? e.code : 'STOP_PIP_ERROR',
        message: "$message. $userAction",
        details: {
          'original_error': e.toString(),
          'user_action': userAction,
          'timestamp': DateTime.now().toIso8601String(),
        },
      );
    }
  }

  /// Start a native countdown test rendered in the PiP window.
  static Future<bool> startCountdownTest(int seconds) async {
    try {
      final result = await _ch.invokeMethod('startCountdownTest', {
        'seconds': seconds,
      });
      debugPrint("✅ Flutter: Countdown test started: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error starting countdown test: $e");
      return false;
    }
  }

  /// Validate screen broadcasting status
  static Future<bool> validateScreenBroadcasting() async {
    try {
      final result = await _ch.invokeMethod('validateScreenBroadcasting');
      debugPrint("✅ Flutter: Screen broadcasting validation triggered: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error validating screen broadcasting: $e");
      return false;
    }
  }

  /// Open Android PiP settings
  static Future<bool> openPiPSettings() async {
    try {
      final result = await _ch.invokeMethod('openPiPSettings');
      debugPrint("✅ Flutter: PiP settings opened: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error opening PiP settings: $e");
      throw PlatformException(
        code: 'SETTINGS_ERROR',
        message: 'Failed to open PiP settings. Go to Settings > Apps > Special app access > Picture-in-picture',
        details: e.toString(),
      );
    }
  }

  /// Enter Activity-based PiP (app-only, live UI updates)
  static Future<bool> enterActivityPiP() async {
    try {
      final result = await _ch.invokeMethod('enterActivityPiP');
      debugPrint("✅ Flutter: enterActivityPiP: $result");
      return result == true;
    } catch (e) {
      debugPrint("❌ Flutter: Error entering Activity PiP: $e");
      return false;
    }
  }
}