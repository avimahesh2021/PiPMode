import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screen_pip.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});


  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool? pipSupported;
  bool? overlayPermission;
  bool? pipPermission;
  bool isLoading = false;
  bool isPiPActive = false;

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    final pipOk = await ScreenPip.isPiPSupported();
    final overlayOk = await ScreenPip.checkOverlayPermission();
    final pipPermissionOk = await ScreenPip.checkPiPPermission();
    setState(() {
      pipSupported = pipOk;
      overlayPermission = overlayOk;
      pipPermission = pipPermissionOk;
    });
    
    // Debug logging to understand permission status
    print("🔍 Permission Status:");
    print("   - PiP Supported: $pipSupported");
    print("   - Overlay Permission: $overlayPermission");
    print("   - PiP Permission: $pipPermission");
  }

  Future<bool> _requestOverlayPermission() async {
    setState(() => isLoading = true);
    final granted = await ScreenPip.requestOverlayPermission();
    await _checkPermissions();
    setState(() => isLoading = false);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(granted
              ? '✅ Overlay permission granted!'
              : '❌ Overlay permission denied. PiP may not work from other apps.'),
          backgroundColor: granted ? Colors.green : Colors.red,
        ),
      );
    }
    return granted;
  }

  Future<void> _startGlobalPiP() async {
    if (pipSupported != true) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('❌ PiP is not supported on this device'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    if (overlayPermission != true) {
      final granted = await _requestOverlayPermission();
      if (!granted) return;
    }

    if (isPiPActive) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('⚠️ PiP is already active'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    setState(() => isLoading = true);
    try {
      final success = await ScreenPip.startLivePiP(withAudio: true);
      setState(() {
        isLoading = false;
        isPiPActive = success;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(success
                ? '📡 Broadcasting started! Check PiP window.'
                : '❌ Failed to start broadcasting. Try selecting "Share entire screen".'),
            backgroundColor: success ? Colors.green : Colors.red,
            action: !success
                ? SnackBarAction(
              label: 'Retry',
              onPressed: _startGlobalPiP,
            )
                : null,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    } catch (e) {
      setState(() {
        isLoading = false;
        isPiPActive = false;
      });
      
      String displayMessage = e.toString();
      String actionLabel = 'Retry';
      Color backgroundColor = Colors.red;
      
      // Extract user-friendly message from PlatformException
      if (e is PlatformException) {
        displayMessage = e.message ?? 'An error occurred';
        
        // Customize action based on error type
        switch (e.code) {
          case 'PERMISSION_DENIED':
            actionLabel = 'Grant Permission';
            break;
          case 'PERMISSION_ERROR':
            actionLabel = 'Settings';
            backgroundColor = Colors.orange;
            break;
          case 'SETUP_ERROR':
          case 'SERVICE_ERROR':
            actionLabel = 'Restart App';
            break;
          default:
            actionLabel = 'Retry';
        }
      }
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ $displayMessage'),
            backgroundColor: backgroundColor,
            action: SnackBarAction(
              label: actionLabel,
              onPressed: e is PlatformException && e.code == 'PERMISSION_ERROR'
                  ? _requestOverlayPermission
                  : _startGlobalPiP,
            ),
            duration: const Duration(seconds: 8),
          ),
        );
      }
    }
  }

  Future<void> _stopGlobalPiP() async {
    setState(() => isLoading = true);
    try {
      await ScreenPip.stopLivePiP();
      setState(() {
        isLoading = false;
        isPiPActive = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('🛑 Broadcasting stopped'),
            backgroundColor: Colors.blue,
          ),
        );
      }
    } catch (e) {
      setState(() {
        isLoading = false;
        isPiPActive = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('❌ Error stopping PiP: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _startBroadcastingWithPiP() async {
    if (pipSupported != true) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('❌ PiP is not supported on this device'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    // Check both overlay and specific PiP permissions
    if (overlayPermission != true) {
      final granted = await _requestOverlayPermission();
      if (!granted) return;
    }
    
    // If PiP permission is already granted, show info message
    if (pipPermission == true) {
      print("✅ PiP permission already granted - should start without asking again");
    }

    if (isPiPActive) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('⚠️ PiP is already active'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    setState(() => isLoading = true);
    try {
      final success = await ScreenPip.startLivePiP(withAudio: true);
      setState(() {
        isLoading = false;
        isPiPActive = success;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(success
                ? '📺 Broadcasting with PIP started!'
                : '❌ Failed to start PIP. Try selecting "Share entire screen".'),
            backgroundColor: success ? Colors.purple : Colors.red,
            action: !success
                ? SnackBarAction(
              label: 'Retry',
              onPressed: _startBroadcastingWithPiP,
            )
                : null,
            duration: const Duration(seconds: 6),
          ),
        );
      }
    } catch (e) {
      setState(() {
        isLoading = false;
        isPiPActive = false;
      });
      
      String displayMessage = e.toString();
      String actionLabel = 'Retry';
      Color backgroundColor = Colors.red;
      
      // Extract user-friendly message from PlatformException
      if (e is PlatformException) {
        displayMessage = e.message ?? 'An error occurred';
        
        // Customize action based on error type
        switch (e.code) {
          case 'PERMISSION_DENIED':
            actionLabel = 'Grant Permission';
            break;
          case 'PERMISSION_ERROR':
            actionLabel = 'Settings';
            backgroundColor = Colors.orange;
            break;
          case 'SETUP_ERROR':
          case 'SERVICE_ERROR':
            actionLabel = 'Restart App';
            break;
          default:
            actionLabel = 'Retry';
        }
      }
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ $displayMessage'),
            backgroundColor: backgroundColor,
            action: SnackBarAction(
              label: actionLabel,
              onPressed: e is PlatformException && e.code == 'PERMISSION_ERROR'
                  ? _requestOverlayPermission
                  : _startBroadcastingWithPiP,
            ),
            duration: const Duration(seconds: 8),
          ),
        );
      }
    }
  }

  Future<void> _validateScreenBroadcasting() async {
    if (!isPiPActive) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('⚠️ PiP is not active - start broadcasting first'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    try {
      final success = await ScreenPip.validateScreenBroadcasting();
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(success
                ? '✅ Screen broadcasting validation triggered! Check the notification for status.'
                : '❌ Failed to validate screen broadcasting.'),
            backgroundColor: success ? Colors.green : Colors.red,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ Error validating screen broadcasting: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 6),
          ),
        );
      }
    }
  }

  Future<void> _openPiPSettings() async {
    try {
      await ScreenPip.openPiPSettings();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('📱 Opening PiP settings...'),
            backgroundColor: Colors.purple,
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ Failed to open settings: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Global Screen PiP"),
        backgroundColor: Colors.deepPurple,
        foregroundColor: Colors.white,
        actions: [
          Stack(
            children: [
              IconButton(
                onPressed: isLoading
                    ? null
                    : () async {
                  if (isPiPActive) {
                    await _stopGlobalPiP();
                  } else {
                    await _startGlobalPiP();
                  }
                },
                icon: Icon(
                  isPiPActive
                      ? Icons.picture_in_picture
                      : (pipSupported == true
                      ? Icons.picture_in_picture_outlined
                      : Icons.picture_in_picture_outlined),
                  color: isPiPActive
                      ? Colors.greenAccent
                      : (pipSupported == true ? Colors.white : Colors.grey),
                ),
                tooltip: isPiPActive ? "Stop PiP Broadcasting" : "Start PiP Broadcasting",
              ),
              if (isPiPActive)
                Positioned(
                  right: 8,
                  top: 8,
                  child: Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              elevation: 4,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Text(
                      "📱 Permission Status",
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Icon(
                          pipSupported == true ? Icons.check_circle : Icons.cancel,
                          color: pipSupported == true ? Colors.green : Colors.red,
                        ),
                        const SizedBox(width: 8),
                        Text("PiP Support: ${pipSupported == true ? 'Available' : 'Not Available'}"),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          overlayPermission == true ? Icons.check_circle : Icons.cancel,
                          color: overlayPermission == true ? Colors.green : Colors.orange,
                        ),
                        const SizedBox(width: 8),
                        Text("Overlay Permission: ${overlayPermission == true ? 'Granted' : 'Not Granted'}"),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          pipPermission == true ? Icons.check_circle : Icons.cancel,
                          color: pipPermission == true ? Colors.green : Colors.red,
                        ),
                        const SizedBox(width: 8),
                        Text("PiP Permission: ${pipPermission == true ? 'Granted' : 'Not Granted'}"),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            if (overlayPermission != true || pipPermission != true) ...[
              Card(
                color: pipPermission != true ? Colors.red.shade50 : Colors.orange.shade50,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      Icon(
                        pipPermission != true ? Icons.picture_in_picture_outlined : Icons.warning, 
                        color: pipPermission != true ? Colors.red : Colors.orange, 
                        size: 48
                      ),
                      const SizedBox(height: 8),
                      Text(
                        pipPermission != true ? "PiP Permission Required" : "Permissions Required",
                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        pipPermission != true 
                          ? "Picture-in-Picture is not enabled for this app. Use 'Open PiP Settings' button below to enable it."
                          : "To access your app screen from anywhere, you need overlay permission. On Android 13+, notification permission is also required for proper operation.",
                      ),
                      const SizedBox(height: 16),
                      if (pipPermission != true) ...[
                        ElevatedButton.icon(
                          onPressed: _openPiPSettings,
                          icon: const Icon(Icons.settings),
                          label: const Text("Open PiP Settings"),
                          style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                        ),
                      ] else ...[
                        ElevatedButton.icon(
                          onPressed: _requestOverlayPermission,
                          icon: const Icon(Icons.security),
                          label: const Text("Grant Required Permissions"),
                          style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
            ],
            Card(
              color: Colors.blue.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Icon(Icons.cast, color: Colors.blue, size: 48),
                    const SizedBox(height: 8),
                    const Text(
                      "📡 Flutter App Broadcasting",
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      "Broadcast your Flutter app content in a floating window!",
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: pipSupported == true ? _startGlobalPiP : null,
                      icon: const Icon(Icons.play_circle_fill),
                      label: const Text("📡 Start Flutter Broadcasting"),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                      ),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: pipSupported == true ? _stopGlobalPiP : null,
                      icon: const Icon(Icons.stop_circle),
                      label: const Text("Stop Broadcasting"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: isPiPActive ? _validateScreenBroadcasting : null,
                      icon: const Icon(Icons.search),
                      label: const Text("Check Screen Broadcasting"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.blue),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _openPiPSettings,
                      icon: const Icon(Icons.settings),
                      label: const Text("Open PiP Settings"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.purple),
                    ),
                    const SizedBox(height: 8),
                    // New: App-only PiP (Activity PiP) for live UI updates
                    OutlinedButton.icon(
                      onPressed: () async {
                        final ok = await ScreenPip.enterActivityPiP();
                        if (mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(ok
                                  ? '✅ Entered App-only PiP (live UI)'
                                  : '❌ Failed to enter App-only PiP'),
                              backgroundColor: ok ? Colors.green : Colors.red,
                              duration: const Duration(seconds: 3),
                            ),
                          );
                        }
                      },
                      icon: const Icon(Icons.picture_in_picture_alt),
                      label: const Text("Enter App-only PiP (Live UI)"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.teal),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Card(
              color: Colors.purple.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Icon(Icons.picture_in_picture, color: Colors.purple, size: 48),
                    const SizedBox(height: 8),
                    const Text(
                      "📺 Broadcasting with PIP",
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      "Start broadcasting with Picture-in-Picture mode enabled!",
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: pipSupported == true ? _startBroadcastingWithPiP : null,
                      icon: const Icon(Icons.airplay),
                      label: const Text("🎬 Broadcasting with PIP"),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.purple,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                      ),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: pipSupported == true ? _stopGlobalPiP : null,
                      icon: const Icon(Icons.stop_circle),
                      label: const Text("Stop Broadcasting with PIP"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.purple),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: isPiPActive ? _validateScreenBroadcasting : null,
                      icon: const Icon(Icons.search),
                      label: const Text("Check Screen Broadcasting"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.blue),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _openPiPSettings,
                      icon: const Icon(Icons.settings),
                      label: const Text("Open PiP Settings"),
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.purple),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Card(
              color: Colors.green.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "📋 How to Use",
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 12),
                    const Text("1. Grant overlay permission (if not already granted)"),
                    const Text("2. Choose your broadcasting option:"),
                    const Text("   • 'Start Flutter Broadcasting' - Standard broadcasting"),
                    const Text("   • 'Broadcasting with PIP' - Enhanced PiP mode"),
                    const Text("3. Grant screen capture permission"),
                    const Text("   ⚠️ IMPORTANT: Select 'Share entire screen' for best results"),
                    const Text("   📱 'Share one app' may cause data issues"),
                    const Text("4. Your device screen appears in a floating PiP window"),
                    const Text("5. Navigate to any app/screen - see your device content!"),
                    const Text("6. Use 'Check Screen Broadcasting' to verify status"),
                    const Text("7. Check notification for broadcasting status:"),
                    const Text("   📺 = Real screen content showing"),
                    const Text("   🎨 = Test pattern (screen capture failed)"),
                    const Text("8. Tap notification to return to main app"),
                    const Text("9. Use 'Stop Broadcasting' when done"),
                    const Text("10. If PiP shows test pattern, re-grant permissions"),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}