import SwiftUI
import UIKit
import ImageIO

@MainActor
enum PhotoPreparation {
    static let maxDimension: CGFloat = 2048

    static func jpeg(from data: Data) throws -> Data {
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: Int(maxDimension),
                kCGImageSourceShouldCacheImmediately: true
              ] as CFDictionary) else { throw APIError(status: 415, message: "تعذر قراءة الصورة المختارة") }
        return try encoded(UIImage(cgImage: thumbnail))
    }

    static func jpeg(from image: UIImage) throws -> Data {
        guard image.size.width > 0, image.size.height > 0 else { throw APIError(status: 415, message: "الصورة غير صالحة") }
        let ratio = min(1, maxDimension / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * ratio, height: image.size.height * ratio)
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.white.setFill(); context.cgContext.fill(CGRect(origin: .zero, size: size))
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return try encoded(resized)
    }

    private static func encoded(_ image: UIImage) throws -> Data {
        guard let data = image.jpegData(compressionQuality: 0.82), data.count <= 10 * 1024 * 1024 else {
            throw APIError(status: 413, message: "تعذر تجهيز الصورة ضمن الحد المسموح")
        }
        return data
    }
}

@MainActor
struct CameraCapture: UIViewControllerRepresentable {
    var onFinish: (Data?) -> Void
    var onFailure: (Error) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish, onFailure: onFailure) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        controller.delegate = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (Data?) -> Void
        let onFailure: (Error) -> Void
        init(onFinish: @escaping (Data?) -> Void, onFailure: @escaping (Error) -> Void) { self.onFinish = onFinish; self.onFailure = onFailure }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onFinish(nil) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            do {
                guard let image = info[.originalImage] as? UIImage else { throw APIError(status: 415, message: "تعذر قراءة الصورة") }
                onFinish(try PhotoPreparation.jpeg(from: image))
            } catch { onFailure(error); onFinish(nil) }
        }
    }
}
@MainActor
struct RecordingSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @StateObject private var recorder = AudioRecorder()
    @State private var attached = false
    var onFinish: (URL) -> Void
    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Image(systemName: recorder.recording ? "waveform" : "mic.fill").font(.system(size: 54)).foregroundStyle(recorder.recording ? .red : MiyarTheme.accent).accessibilityHidden(true)
                Text(String(format: "%02d:%02d", recorder.seconds / 60, recorder.seconds % 60)).monospacedDigit().font(.largeTitle).environment(\.layoutDirection, .leftToRight)
                Text(recorder.recording ? "جارٍ التسجيل" : "التسجيل جاهز").foregroundStyle(.secondary)
                if let error = recorder.error { InlineError(text: error) }
                Button("إرفاق التسجيل", systemImage: "checkmark") { if let file = recorder.finish() { attached = true; onFinish(file); dismiss() } }.buttonStyle(.borderedProminent).disabled(recorder.seconds == 0)
                Text("حتى 3 دقائق").font(.footnote).foregroundStyle(.secondary)
            }.padding().navigationTitle("رسالة صوتية").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إلغاء", systemImage: "xmark") { recorder.cancel(); dismiss() } } }
                .task { await recorder.start() }
                .onDisappear { if attached { _ = recorder.finish() } else { recorder.cancel() } }
                .onChange(of: phase) { _, value in if value == .background { recorder.cancel(); dismiss() } }
        }.presentationDetents([.medium])
    }
}
@MainActor
struct MessageAttachmentView: View {
    let attachment: Attachment
    let mine: Bool
    @State private var image: UIImage?
    @State private var file: SharedFile?
    @State private var localURL: URL?
    @State private var loading = false
    @State private var error: String?
    @StateObject private var audio = AudioPlayback()
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if attachment.mime.hasPrefix("image/") {
                Button { Task { await open() } } label: {
                    Group { if let image { Image(uiImage: image).resizable().scaledToFill() } else { ZStack { MiyarTheme.soft; ProgressView() } } }.frame(width: 220, height: 160).clipShape(RoundedRectangle(cornerRadius: 16))
                }.buttonStyle(.plain).accessibilityLabel("عرض الصورة")
                    .task(id: attachment.id) { do { image = UIImage(data: try await APIClient.shared.bytes(attachment)) } catch { self.error = error.localizedDescription } }
            } else if attachment.mime.hasPrefix("audio/") {
                Button {
                    Task {
                        loading = true; defer { loading = false }
                        do { let url: URL; if let localURL { url = localURL } else { url = try await APIClient.shared.localFile(attachment); localURL = url }; try audio.toggle(url: url) }
                        catch { self.error = error.localizedDescription }
                    }
                } label: {
                    HStack(spacing: 12) { if loading { ProgressView() } else { Image(systemName: audio.playing ? "pause.fill" : "play.fill") }; Image(systemName: "waveform").font(.title2); Text("رسالة صوتية").font(MiyarTheme.font(14)) }.padding(10).foregroundStyle(mine ? .white : .primary)
                }.buttonStyle(.plain).disabled(loading).accessibilityLabel(audio.playing ? "إيقاف التسجيل" : "تشغيل التسجيل")
            } else {
                Button { Task { await open() } } label: {
                    HStack { Image(systemName: "doc.fill"); VStack(alignment: .leading) { Text(attachment.name).lineLimit(2); Text(ByteCountFormatter.string(fromByteCount: Int64(attachment.bytes), countStyle: .file)).font(.caption) }; if loading { ProgressView() } }
                }.buttonStyle(.plain).padding(6).disabled(loading).foregroundStyle(mine ? .white : .primary)
            }
            if let error { Text(error).font(.caption).foregroundStyle(mine ? .white : .red) }
        }.sheet(item: $file) { FileSheet(file: $0) }.onDisappear { audio.stop() }
    }
    private func open() async { loading = true; defer { loading = false }; do { file = SharedFile(url: try await APIClient.shared.localFile(attachment)) } catch { self.error = error.localizedDescription } }
}
