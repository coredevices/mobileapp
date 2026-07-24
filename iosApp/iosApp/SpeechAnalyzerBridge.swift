import Foundation
import AVFoundation
import Speech
import ComposeApp

/// Registers the iOS 26+ SpeechAnalyzer/SpeechTranscriber engine with the Kotlin side.
/// SpeechAnalyzer is a Swift-only API, so Kotlin calls back into these closures.
enum SpeechAnalyzerBridge {
    // Sessions are serialized by a mutex on the Kotlin side, so one handle suffices. The lock
    // orders the write against a cancel arriving on another thread.
    private static let taskLock = NSLock()
    nonisolated(unsafe) private static var currentTask: Task<Void, Never>?

    static func register() {
        IOSDelegate.shared.registerNativeSpeechAnalyzer(
            isSupported: {
                if #available(iOS 26.0, *) {
                    return KotlinBoolean(true)
                }
                return KotlinBoolean(false)
            },
            cancelTranscription: {
                taskLock.lock()
                let task = currentTask
                taskLock.unlock()
                task?.cancel()
            },
            transcribeWavFile: { path, localeTag, completion in
                guard #available(iOS 26.0, *) else {
                    completion(nil, "SpeechAnalyzer requires iOS 26+")
                    return
                }
                let task = Task {
                    do {
                        let text = try await transcribe(path: path, localeTag: localeTag)
                        completion(text, nil)
                    } catch is CancellationError {
                        completion(nil, "cancelled")
                    } catch {
                        completion(nil, String(describing: error))
                    }
                }
                taskLock.lock()
                currentTask = task
                taskLock.unlock()
            }
        )
        Task {
            if #available(iOS 26.0, *) {
                let transcriber = await SpeechTranscriber.supportedLocales
                let dictation = await DictationTranscriber.supportedLocales
                let tags = Array(Set((transcriber + dictation).map { $0.identifier(.bcp47) })).sorted()
                IOSDelegate.shared.setNativeSpeechAnalyzerLanguages(tags: tags)
            }
        }
    }

    @available(iOS 26.0, *)
    private static func transcribe(path: String, localeTag: String?) async throws -> String {
        let requested = localeTag.map { Locale(identifier: $0) } ?? Locale.current
        let audioFile = try AVAudioFile(forReading: URL(fileURLWithPath: path))

        // SpeechTranscriber needs the Apple Intelligence model assets; fall back to
        // DictationTranscriber where they're unavailable (older devices, simulator).
        if let locale = await SpeechTranscriber.supportedLocale(equivalentTo: requested) {
            do {
                let transcriber = SpeechTranscriber(locale: locale, preset: .transcription)
                return try await run(transcriber, audioFile: audioFile) {
                    var pieces: [String] = []
                    for try await result in transcriber.results where result.isFinal {
                        pieces.append(String(result.text.characters))
                    }
                    return pieces.joined(separator: " ")
                }
            } catch {
                NSLog("SpeechAnalyzerBridge: SpeechTranscriber failed (\(error)), falling back to DictationTranscriber")
            }
        }
        guard let locale = await DictationTranscriber.supportedLocale(equivalentTo: requested) else {
            throw BridgeError("Locale \(requested.identifier) not supported by SpeechTranscriber or DictationTranscriber")
        }
        let transcriber = DictationTranscriber(locale: locale, preset: .longDictation)
        return try await run(transcriber, audioFile: audioFile) {
            var pieces: [String] = []
            for try await result in transcriber.results where result.isFinal {
                pieces.append(String(result.text.characters))
            }
            return pieces.joined(separator: " ")
        }
    }

    @available(iOS 26.0, *)
    private static func run(
        _ module: some SpeechModule,
        audioFile: AVAudioFile,
        collect: @escaping @Sendable () async throws -> String,
    ) async throws -> String {
        if let installation = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
            try await installation.downloadAndInstall()
        }
        let analyzer = SpeechAnalyzer(modules: [module])
        let collector = Task {
            try await collect().trimmingCharacters(in: .whitespacesAndNewlines)
        }
        do {
            if let lastSampleTime = try await analyzer.analyzeSequence(from: audioFile) {
                try await analyzer.finalizeAndFinish(through: lastSampleTime)
            } else {
                // No audio samples: cancel the collector too — the results sequence may
                // never end after cancelAndFinishNow(), which would hang collector.value.
                await analyzer.cancelAndFinishNow()
                collector.cancel()
                return ""
            }
        } catch {
            collector.cancel()
            throw error
        }
        return try await collector.value
    }

    private struct BridgeError: Error, CustomStringConvertible {
        let description: String
        init(_ description: String) { self.description = description }
    }
}
