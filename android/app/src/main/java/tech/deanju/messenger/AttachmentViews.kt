package tech.deanju.messenger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.*

class AudioPlayback {
    var active by mutableStateOf<String?>(null); private set
    private var player: MediaPlayer? = null
    fun play(id: String, file: File, onError: () -> Unit) {
        if (active == id) { stop(); return }
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start(); active = id }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); onError(); true }
                prepareAsync()
            }
        } catch (_: Exception) { stop(); onError() }
    }
    fun stop() { player?.release(); player = null; active = null }
}
private fun image(file: File, maximum: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maximum) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable fun AttachmentContent(attachment: Attachment, model: ChatModel, player: AudioPlayback, mine: Boolean, preview: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    if (attachment.mime.startsWith("image/")) {
        val bitmap by produceState<Bitmap?>(null, attachment.id) {
            try { val file = model.api.file(attachment); value = withContext(Dispatchers.IO) { image(file, 800) } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "صورة مرفقة", Modifier.widthIn(max = 270.dp).heightIn(max = 280.dp).clickable(onClick = preview), contentScale = ContentScale.Fit)
        else TextButton(preview) { Icon(Icons.Default.Photo, null, tint = if (mine) Color.White else MiyarBlue); Spacer(Modifier.width(8.dp)); Text("عرض الصورة", color = if (mine) Color.White else MiyarBlue) }
    } else if (attachment.mime.startsWith("audio/")) {
        Row(Modifier.clickable(enabled = !busy) { scope.launch {
            busy = true
            try { val file = model.api.file(attachment); player.play(attachment.id, file) { model.error = "تعذر تشغيل التسجيل" } }
            catch (e: Exception) { model.report(e) } finally { busy = false }
        } }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = if (mine) Color.White else MiyarBlue)
            else Icon(if (player.active == attachment.id) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, if (player.active == attachment.id) "إيقاف التسجيل" else "تشغيل التسجيل", Modifier.size(34.dp))
            Column { Text("رسالة صوتية", style = MaterialTheme.typography.bodyMedium); Text("${attachment.bytes / 1024} كيلوبايت", style = MaterialTheme.typography.labelSmall) }
            Icon(Icons.Default.GraphicEq, null)
        }
    } else Row(Modifier.clickable(onClick = preview).padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(30.dp)); Column { Text(attachment.name, style = MaterialTheme.typography.bodyMedium); Text("عرض الملف · ${attachment.bytes / 1024} كيلوبايت", style = MaterialTheme.typography.labelSmall) } }
}

@Composable fun AttachmentPreview(attachment: Attachment, model: ChatModel, dismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }; var count by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(null, attachment.id, page, retry) {
        value = null; error = null
        try {
            val file = model.api.file(attachment)
            if (attachment.mime == "application/pdf") {
                val rendered = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdf ->
                            val width = 1200; val height = (width.toDouble() * pdf.height / pdf.width).toInt().coerceAtMost(6000)
                            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                            pdf.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result to renderer.pageCount
                        }
                    } }
                }
                value = rendered.first; count = rendered.second
            } else value = withContext(Dispatchers.IO) { image(file, 2048) }
        } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "تعذر فتح الملف" }
    }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(dismiss) { Icon(Icons.Default.Close, "إغلاق") }; Text(attachment.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2) }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it.asImageBitmap(), attachment.name, Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.FillWidth) }
                    if (bitmap == null && error == null) CircularProgressIndicator(Modifier.padding(32.dp).size(28.dp), strokeWidth = 2.dp)
                    error?.let { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { ErrorText(it); TextButton({ retry++ }) { Text("إعادة المحاولة") } } }
                }
                if (count > 0) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ page-- }, enabled = page > 0) { Text("السابقة") }; Text("${page + 1} / $count", style = MaterialTheme.typography.bodyMedium); TextButton({ page++ }, enabled = page + 1 < count) { Text("التالية") }
                }
            }
        }
    }
}
