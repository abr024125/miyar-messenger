package tech.deanju.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedMedia(val bytes: ByteArray, val name: String, val mime: String)
object MediaTools {
    suspend fun read(context: Context, uri: Uri): PickedMedia = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "مرفق"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0).orEmpty().take(150) }
        val mime = resolver.getType(uri).orEmpty()
        val maximum = if (mime.startsWith("image/")) 30 * 1024 * 1024 else 10 * 1024 * 1024
        val data = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= maximum) { "الملف أكبر من الحجم المسموح" }; out.write(buffer, 0, n) }
            out.toByteArray()
        } ?: throw IllegalArgumentException("تعذرت قراءة الملف")
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "الصورة غير صالحة" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IllegalArgumentException("الصورة غير صالحة")
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val output = ByteArrayOutputStream(); rotated.compress(Bitmap.CompressFormat.JPEG, 87, output)
            if (rotated !== bitmap) rotated.recycle(); bitmap.recycle()
            PickedMedia(output.toByteArray(), name.substringBeforeLast('.', name) + ".jpg", "image/jpeg")
        } else {
            require(mime == "application/pdf" && data.take(5).toByteArray().decodeToString() == "%PDF-") { "اختاري صورة أو ملف PDF" }
            PickedMedia(data, name.ifBlank { "ملف.pdf" }, mime)
        }
    }
}

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    fun start(onLimit: () -> Unit) {
        cancel()
        val directory = File(context.cacheDir, "voice").apply { mkdirs() }
        val output = File.createTempFile("recording-", ".m4a", directory); file = output
        @Suppress("DEPRECATION") val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC); next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); next.setAudioEncodingBitRate(64000); next.setAudioSamplingRate(44100)
            next.setOutputFile(output.absolutePath); next.setMaxDuration(180000)
            next.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            next.prepare(); next.start(); recorder = next
        } catch (e: Exception) { next.release(); output.delete(); file = null; throw e }
    }
    fun finish(): File? {
        val current = recorder ?: return null; recorder = null; val output = file; file = null
        return try { current.stop(); output?.takeIf { it.length() > 0 } } catch (_: Exception) { output?.delete(); null } finally { current.release() }
    }
    fun cancel() { finish()?.delete() }
}
