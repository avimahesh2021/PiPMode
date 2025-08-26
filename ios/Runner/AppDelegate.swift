import Flutter
import UIKit
import ReplayKit
import AVFoundation
import AVKit

@main
@objc class AppDelegate: FlutterAppDelegate {
    
    // MARK: - iOS PiP Screen Recording Implementation
    private var screenRecorder: RPScreenRecorder?
    private var pipController: AVPictureInPictureController?
    private var playerViewController: AVPlayerViewController?
    private var player: AVPlayer?
    private var methodChannel: FlutterMethodChannel?
    private var isRecording = false
    private var videoWriter: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var outputURL: URL?
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        
        print("🚀 iOS PiP Service Starting...")
        
        GeneratedPluginRegistrant.register(with: self)
        
        // Setup method channel for Flutter communication
        setupMethodChannel()
        
        // Setup ReplayKit screen recording
        setupScreenRecording()
        
        // Setup background audio for PiP
        setupAudioSession()
        
        print("✅ iOS PiP Service initialized successfully")
        
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
    
    // MARK: - Method Channel Setup
    private func setupMethodChannel() {
        guard let controller = window?.rootViewController as? FlutterViewController else {
            print("❌ Could not get Flutter view controller")
            return
        }
        
        methodChannel = FlutterMethodChannel(name: "pip_channel", binaryMessenger: controller.binaryMessenger)
        
        methodChannel?.setMethodCallHandler { [weak self] (call, result) in
            guard let self = self else { return }
            
            print("📨 iOS Method call: \(call.method)")
            
            switch call.method {
            case "isPiPSupported":
                self.checkPiPSupport(result: result)
                
            case "checkOverlayPermission":
                self.checkScreenRecordingPermission(result: result)
                
            case "requestOverlayPermission":
                self.requestScreenRecordingPermission(result: result)
                
            case "startLivePiP":
                self.startLivePiP(result: result)
                
            case "stopLivePiP":
                self.stopLivePiP(result: result)
                
            case "validateScreenBroadcasting":
                self.validateScreenBroadcasting(result: result)
                
            case "openPiPSettings":
                self.openPiPSettings(result: result)
                
            case "checkPiPPermission":
                self.checkPiPPermission(result: result)
                
            default:
                result(FlutterMethodNotImplemented)
            }
        }
        
        print("✅ iOS Method channel setup completed")
    }
    
    // MARK: - ReplayKit Screen Recording Setup
    private func setupScreenRecording() {
        screenRecorder = RPScreenRecorder.shared()
        print("✅ ReplayKit screen recorder initialized")
    }
    
    // MARK: - Audio Session Setup for PiP
    private func setupAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .moviePlayback, options: [.allowAirPlay, .allowBluetooth])
            try audioSession.setActive(true)
            print("✅ Audio session configured for PiP")
        } catch {
            print("❌ Audio session setup failed: \(error)")
        }
    }
    
    // MARK: - Method Channel Handlers
    
    private func checkPiPSupport(result: @escaping FlutterResult) {
        let isSupported = AVPictureInPictureController.isPictureInPictureSupported()
        print("🔍 iOS PiP Support: \(isSupported)")
        result(isSupported)
    }
    
    private func checkScreenRecordingPermission(result: @escaping FlutterResult) {
        // iOS ReplayKit doesn't have a way to check permission status beforehand
        // We'll return true and handle permission in the recording request
        print("🔍 iOS Screen Recording Permission: Available")
        result(true)
    }
    
    private func requestScreenRecordingPermission(result: @escaping FlutterResult) {
        // iOS handles permission request automatically when starting recording
        print("🔍 iOS Screen Recording Permission: Will be requested on record start")
        result(true)
    }
    
    private func checkPiPPermission(result: @escaping FlutterResult) {
        let isSupported = AVPictureInPictureController.isPictureInPictureSupported()
        print("🔍 iOS PiP Permission: \(isSupported)")
        result(isSupported)
    }
    
    private func startLivePiP(result: @escaping FlutterResult) {
        print("🚀 Starting iOS Live PiP...")
        
        guard let screenRecorder = screenRecorder else {
            print("❌ Screen recorder not available")
            result(FlutterError(code: "SETUP_ERROR", message: "Screen recorder not initialized", details: nil))
            return
        }
        
        guard !isRecording else {
            print("⚠️ Recording already in progress")
            result(true)
            return
        }
        
        // Check if screen recording is available
        guard screenRecorder.isAvailable else {
            print("❌ Screen recording not available on this device")
            result(FlutterError(code: "DEVICE_ERROR", message: "Screen recording not supported on this device", details: nil))
            return
        }
        
        // For iOS, we'll use a simpler approach:
        // 1. Start screen recording to create a video file
        // 2. Use that video file for PiP playback
        print("📹 Starting ReplayKit screen recording...")
        
        // Start recording to a file (simpler approach than real-time capture)
        screenRecorder.isMicrophoneEnabled = true
        screenRecorder.isCameraEnabled = false
        
        // Create output URL for the recording
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        outputURL = documentsPath.appendingPathComponent("screen_recording_\(Date().timeIntervalSince1970).mp4")
        
        screenRecorder.startRecording { [weak self] (error) in
            guard let self = self else { return }
            
            if let error = error {
                print("❌ Failed to start screen recording: \(error)")
                result(FlutterError(code: "CAPTURE_ERROR", message: "Failed to start screen recording: \(error.localizedDescription)", details: nil))
                return
            }
            
            print("✅ Screen recording started successfully")
            self.isRecording = true
            
            // Start creating PiP with a demo video first (will be replaced with actual recording)
            self.createDemoPiPPlayer { success in
                DispatchQueue.main.async {
                    if success {
                        print("🎉 iOS PiP started successfully")
                        result(true)
                    } else {
                        print("❌ Failed to create PiP player")
                        result(FlutterError(code: "PIP_ERROR", message: "Failed to create PiP player", details: nil))
                    }
                }
            }
        }
    }
    
    private func stopLivePiP(result: @escaping FlutterResult) {
        print("🛑 Stopping iOS Live PiP...")
        
        // Stop screen recording
        if isRecording {
            screenRecorder?.stopRecording { [weak self] (previewViewController, error) in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ Error stopping screen recording: \(error)")
                } else {
                    print("✅ Screen recording stopped")
                    
                    // Optionally handle the preview view controller
                    previewViewController?.dismiss(animated: true)
                }
                
                self.isRecording = false
            }
        }
        
        // Stop PiP
        if let pipController = pipController, pipController.isPictureInPictureActive {
            pipController.stopPictureInPicture()
        }
        
        // Cleanup player
        player?.pause()
        player = nil
        playerViewController = nil
        pipController = nil
        
        print("✅ iOS PiP stopped successfully")
        result(true)
    }
    
    private func validateScreenBroadcasting(result: @escaping FlutterResult) {
        let isValid = isRecording && 
                     screenRecorder?.isRecording == true &&
                     pipController?.isPictureInPictureActive == true
        
        print("🔍 iOS Screen Broadcasting Validation: \(isValid)")
        print("   - Recording: \(isRecording)")
        print("   - ReplayKit active: \(screenRecorder?.isRecording == true)")
        print("   - PiP active: \(pipController?.isPictureInPictureActive == true)")
        
        result(isValid)
    }
    
    private func openPiPSettings(result: @escaping FlutterResult) {
        print("🔧 Opening iOS PiP Settings...")
        
        // iOS doesn't have specific PiP settings, but we can open general settings
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl) { success in
                    print("📱 Settings opened: \(success)")
                    result(success)
                }
            } else {
                print("❌ Cannot open settings")
                result(false)
            }
        } else {
            print("❌ Settings URL not available")
            result(false)
        }
    }
    
    // MARK: - Demo PiP Player Creation (Simplified Approach)
    
    private func createDemoPiPPlayer(completion: @escaping (Bool) -> Void) {
        print("🎬 Creating iOS Demo PiP player...")
        
        // For the demo, we'll create a simple colored view that represents screen content
        // In a full implementation, this would be the actual screen recording
        
        // Create a test video URL (we'll use a color animation for demo)
        createTestVideo { [weak self] videoURL in
            guard let self = self, let videoURL = videoURL else {
                print("❌ Failed to create test video")
                completion(false)
                return
            }
            
            // Create AVPlayer with the test video
            self.player = AVPlayer(url: videoURL)
            self.playerViewController = AVPlayerViewController()
            self.playerViewController?.player = self.player
            
            // Setup PiP controller
            if AVPictureInPictureController.isPictureInPictureSupported() {
                
                // Create a player layer for PiP
                let playerLayer = AVPlayerLayer(player: self.player)
                playerLayer.videoGravity = .resizeAspectFill
                
                self.pipController = AVPictureInPictureController(playerLayer: playerLayer)
                self.pipController?.delegate = self
                
                // Present the player view controller
                DispatchQueue.main.async {
                    if let rootViewController = self.window?.rootViewController {
                        rootViewController.present(self.playerViewController!, animated: true) {
                            // Start PiP mode
                            self.pipController?.startPictureInPicture()
                            self.player?.play()
                            
                            // Loop the video
                            NotificationCenter.default.addObserver(
                                forName: .AVPlayerItemDidPlayToEndTime,
                                object: self.player?.currentItem,
                                queue: .main
                            ) { _ in
                                self.player?.seek(to: CMTime.zero)
                                self.player?.play()
                            }
                            
                            print("✅ iOS Demo PiP player created and started")
                            completion(true)
                        }
                    } else {
                        print("❌ No root view controller available")
                        completion(false)
                    }
                }
            } else {
                print("❌ PiP not supported")
                completion(false)
            }
        }
    }
    
    // MARK: - Test Video Creation
    
    private func createTestVideo(completion: @escaping (URL?) -> Void) {
        print("🎨 Creating test video for PiP demo...")
        
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let outputURL = documentsPath.appendingPathComponent("demo_pip_video.mp4")
        
        // Remove existing file
        if FileManager.default.fileExists(atPath: outputURL.path) {
            try? FileManager.default.removeItem(at: outputURL)
        }
        
        guard let videoWriter = try? AVAssetWriter(outputURL: outputURL, fileType: .mp4) else {
            print("❌ Failed to create video writer")
            completion(nil)
            return
        }
        
        let videoSettings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: 400,
            AVVideoHeightKey: 600,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 1000000,
            ]
        ]
        
        let videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        let pixelBufferAdaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: videoInput,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
                kCVPixelBufferWidthKey as String: 400,
                kCVPixelBufferHeightKey as String: 600
            ]
        )
        
        videoInput.expectsMediaDataInRealTime = false
        videoWriter.add(videoInput)
        
        videoWriter.startWriting()
        videoWriter.startSession(atSourceTime: CMTime.zero)
        
        let duration = CMTime(seconds: 3, preferredTimescale: 30) // 3 second loop
        let frameRate: Int32 = 30
        let totalFrames = Int(duration.seconds) * Int(frameRate)
        
        var frameCount = 0
        
        videoInput.requestMediaDataWhenReady(on: DispatchQueue.global()) {
            while videoInput.isReadyForMoreMediaData && frameCount < totalFrames {
                let frameTime = CMTime(value: Int64(frameCount), timescale: frameRate)
                
                if let pixelBuffer = self.createPixelBuffer(frameCount: frameCount, totalFrames: totalFrames) {
                    pixelBufferAdaptor.append(pixelBuffer, withPresentationTime: frameTime)
                }
                
                frameCount += 1
            }
            
            if frameCount >= totalFrames {
                videoInput.markAsFinished()
                videoWriter.finishWriting {
                    print("✅ Test video created successfully")
                    completion(outputURL)
                }
            }
        }
    }
    
    private func createPixelBuffer(frameCount: Int, totalFrames: Int) -> CVPixelBuffer? {
        let width = 400
        let height = 600
        
        var pixelBuffer: CVPixelBuffer?
        let result = CVPixelBufferCreate(
            nil, width, height,
            kCVPixelFormatType_32ARGB,
            nil, &pixelBuffer
        )
        
        guard result == kCVReturnSuccess, let buffer = pixelBuffer else {
            return nil
        }
        
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        
        let baseAddress = CVPixelBufferGetBaseAddress(buffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(buffer)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        
        guard let context = CGContext(
            data: baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue
        ) else {
            return nil
        }
        
        // Create animated color effect
        let progress = Float(frameCount) / Float(totalFrames)
        let hue = progress * 360.0
        let color = UIColor(hue: CGFloat(hue / 360.0), saturation: 0.8, brightness: 0.9, alpha: 1.0)
        
        context.setFillColor(color.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        
        // Add some text
        let text = "📱 iOS PiP Demo\n🎬 Screen Recording\n\(frameCount + 1)/\(totalFrames)"
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 24),
            .foregroundColor: UIColor.white,
            .strokeColor: UIColor.black,
            .strokeWidth: -2
        ]
        
        let attributedText = NSAttributedString(string: text, attributes: attributes)
        let textRect = CGRect(x: 50, y: height/2 - 50, width: width - 100, height: 100)
        
        UIGraphicsPushContext(context)
        attributedText.draw(in: textRect)
        UIGraphicsPopContext()
        
        return buffer
    }
}

// MARK: - AVPictureInPictureControllerDelegate

extension AppDelegate: AVPictureInPictureControllerDelegate {
    
    func pictureInPictureControllerWillStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("▶️ iOS PiP will start")
    }
    
    func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("✅ iOS PiP started successfully")
    }
    
    func pictureInPictureControllerWillStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("⏸️ iOS PiP will stop")
    }
    
    func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("🛑 iOS PiP stopped")
    }
    
    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
        print("❌ iOS PiP failed to start: \(error)")
    }
    
    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        print("🔄 Restoring user interface from PiP")
        completionHandler(true)
    }
}
