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
            },
            assetStatus: { localeTag, completion in
                guard #available(iOS 26.0, *) else {
                    completion(AssetStatus.unsupported.rawValue)
                    return
                }
                Task {
                    let status = await resolveEngine(localeTag: localeTag)?.status ?? .unsupported
                    completion(status.rawValue)
                }
            },
            downloadAssets: { localeTag, onProgress, completion in
                guard #available(iOS 26.0, *) else {
                    completion("SpeechAnalyzer requires iOS 26+")
                    return
                }
                Task {
                    do {
                        try await downloadAssets(localeTag: localeTag) { fraction in
                            onProgress(KotlinDouble(double: fraction))
                        }
                        completion(nil)
                    } catch {
                        completion(String(describing: error))
                    }
                }
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

    /// Mirrors `NativeSpeechAnalyzerBridge.AssetStatus` on the Kotlin side.
    private enum AssetStatus: String {
        case installed, downloading, notDownloaded, unsupported

        @available(iOS 26.0, *)
        init(_ status: AssetInventory.Status) {
            switch status {
            case .installed: self = .installed
            case .downloading: self = .downloading
            case .supported: self = .notDownloaded
            case .unsupported: self = .unsupported
            @unknown default: self = .unsupported
            }
        }
    }

    /// SpeechTranscriber needs the Apple Intelligence model assets; DictationTranscriber covers
    /// devices where they're unavailable (older hardware, simulator).
    @available(iOS 26.0, *)
    private enum Engine {
        case transcriber(SpeechTranscriber)
        case dictation(DictationTranscriber)

        var module: any SpeechModule {
            switch self {
            case .transcriber(let module): return module
            case .dictation(let module): return module
            }
        }
    }

    @available(iOS 26.0, *)
    private static func candidateEngines(for requested: Locale) async -> [Engine] {
        var engines: [Engine] = []
        if let locale = await SpeechTranscriber.supportedLocale(equivalentTo: requested) {
            engines.append(.transcriber(SpeechTranscriber(locale: locale, preset: .transcription)))
        }
        if let locale = await DictationTranscriber.supportedLocale(equivalentTo: requested) {
            engines.append(.dictation(DictationTranscriber(locale: locale, preset: .longDictation)))
        }
        return engines
    }

    /// The first engine this device can run for the locale, with its asset install state.
    /// Both transcription and downloads go through this so they agree on which assets matter.
    @available(iOS 26.0, *)
    private static func resolveEngine(localeTag: String?) async -> (engine: Engine, status: AssetStatus)? {
        let requested = localeTag.map { Locale(identifier: $0) } ?? Locale.current
        for engine in await candidateEngines(for: requested) {
            let status = AssetStatus(await AssetInventory.status(forModules: [engine.module]))
            if status != .unsupported {
                return (engine, status)
            }
        }
        return nil
    }

    @available(iOS 26.0, *)
    private static func downloadAssets(
        localeTag: String?,
        onProgress: @escaping @Sendable (Double) -> Void,
    ) async throws {
        guard let resolved = await resolveEngine(localeTag: localeTag) else {
            throw BridgeError("Locale \(localeTag ?? Locale.current.identifier) not supported")
        }
        guard let installation = try await AssetInventory.assetInstallationRequest(supporting: [resolved.engine.module]) else {
            return
        }
        let observation = installation.progress.observe(\.fractionCompleted, options: [.initial, .new]) { progress, _ in
            onProgress(progress.fractionCompleted)
        }
        defer { observation.invalidate() }
        try await installation.downloadAndInstall()
    }

    @available(iOS 26.0, *)
    private static func transcribe(path: String, localeTag: String?) async throws -> String {
        guard let resolved = await resolveEngine(localeTag: localeTag) else {
            throw BridgeError("Locale \(localeTag ?? Locale.current.identifier) not supported")
        }
        // Kotlin checks this before calling; it's a guard so a race can't start a session
        // without assets, which would otherwise fail or stall.
        guard resolved.status == .installed else {
            throw BridgeError("Speech assets for \(localeTag ?? Locale.current.identifier) are \(resolved.status.rawValue)")
        }
        let audioFile = try AVAudioFile(forReading: URL(fileURLWithPath: path))
        switch resolved.engine {
        case .transcriber(let transcriber):
            return try await run(transcriber, audioFile: audioFile) {
                var pieces: [String] = []
                for try await result in transcriber.results where result.isFinal {
                    pieces.append(String(result.text.characters))
                }
                return pieces.joined(separator: " ")
            }
        case .dictation(let transcriber):
            return try await run(transcriber, audioFile: audioFile) {
                var pieces: [String] = []
                for try await result in transcriber.results where result.isFinal {
                    pieces.append(String(result.text.characters))
                }
                return pieces.joined(separator: " ")
            }
        }
    }

    @available(iOS 26.0, *)
    private static func run(
        _ module: some SpeechModule,
        audioFile: AVAudioFile,
        collect: @escaping @Sendable () async throws -> String,
    ) async throws -> String {
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
