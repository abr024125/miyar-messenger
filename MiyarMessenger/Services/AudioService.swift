import AVFoundation
import Combine

@MainActor
final class AudioRecorder: ObservableObject {
    @Published private(set) var recording = false
    @Published private(set) var seconds = 0
    @Published var error: String?
    private var recorder: AVAudioRecorder?
    private var tick: Task<Void, Never>?
    private var file: URL?
    func start() async {
        let permitted = await withCheckedContinuation { continuation in AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning: $0) } }
        guard !Task.isCancelled else { return }
        guard permitted else { error = "اسمحي بالوصول للميكروفون من إعدادات الآيفون"; return }
        do {
            AudioPlayback.stopActive()
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".m4a")
            let value = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 44100, AVNumberOfChannelsKey: 1, AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue])
            guard value.record() else { throw APIError(status: 500, message: "تعذر بدء التسجيل") }
            try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: url.path)
            file = url; recorder = value; recording = true; seconds = 0
            tick = Task { [weak self] in
                while !Task.isCancelled {
                    do { try await Task.sleep(for: .seconds(1)) } catch { return }
                    guard let self else { return }; self.seconds = Int(self.recorder?.currentTime ?? 0)
                    if self.seconds >= 180 { _ = self.finish(); return }
                }
            }
        } catch { self.error = error.localizedDescription; cancel() }
    }
    func finish() -> URL? { recorder?.stop(); tick?.cancel(); recording = false; try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation); return file }
    func cancel() { let file = finish(); if let file { try? FileManager.default.removeItem(at: file) }; self.file = nil }
}
@MainActor
final class AudioPlayback: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var playing = false
    private static weak var active: AudioPlayback?
    private var player: AVAudioPlayer?
    static func stopActive() { active?.stop() }
    func toggle(url: URL) throws {
        if playing { player?.pause(); playing = false; return }
        try AVAudioSession.sharedInstance().setCategory(.playback)
        try AVAudioSession.sharedInstance().setActive(true)
        if player == nil {
            player = try AVAudioPlayer(contentsOf: url); player?.delegate = self
        }
        if let active = Self.active, active !== self { active.stop() }; Self.active = self
        player?.play(); playing = true
    }
    func stop() { player?.stop(); playing = false }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { Task { @MainActor in self.playing = false } }
}
