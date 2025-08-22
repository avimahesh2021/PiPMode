import 'package:flutter/material.dart';
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

  @override
  void initState() {
    super.initState();
    _checkPiPSupport();
  }

  Future<void> _checkPiPSupport() async {
    final ok = await ScreenPip.isPiPSupported();
    setState(() => pipSupported = ok);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Screen PiP Example")),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (pipSupported == null)
              const CircularProgressIndicator()
            else if (pipSupported == true)
              const Text("✅ PiP Supported", style: TextStyle(fontSize: 18))
            else
              const Text("❌ PiP Not Supported", style: TextStyle(fontSize: 18)),

            const SizedBox(height: 30),

            // Normal Flutter PiP (shrinks current UI)
            ElevatedButton(
              onPressed: pipSupported == true
                  ? () => ScreenPip.enterPiP()
                  : null,
              child: const Text("Enter Normal PiP"),
            ),

            const SizedBox(height: 20),

            // Start live screen mirror with MediaProjection
            ElevatedButton(
              onPressed: pipSupported == true
                  ? () => ScreenPip.startLivePiP(withAudio: true)
                  : null,
              child: const Text("Start Live Screen PiP"),
            ),

            ElevatedButton(
              onPressed: pipSupported == true
                  ? () => ScreenPip.stopLivePiP()
                  : null,
              child: const Text("Stop Live Screen PiP"),
            ),
          ],
        ),
      ),
    );
  }
}
