@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package tech.deanju.messenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun ThreadScreen(model: ChatModel) {
    val chat = model.conversation ?: return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var info by remember { mutableStateOf(false) }; var search by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Message?>(null) }
    var delete by remember { mutableStateOf<Message?>(null) }
    var recording by remember { mutableStateOf(false) }; var seconds by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Attachment?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val recorder = remember { VoiceRecorder(context) }
    val player = remember { AudioPlayback() }
    fun finishRecording() { recording = false; recorder.finish()?.let { model.recordFile(it) } }
    fun startRecording() {
        try { recorder.start { finishRecording() }; seconds = 0; recording = true }
        catch (_: Exception) { model.error = "تعذر تشغيل الميكروفون" }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startRecording() else model.error = "اسمحي باستخدام الميكروفون لتسجيل رسالة صوتية" }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.attach(it) } }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.attach(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraFile?.let { file -> if (ok) model.attach(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) else { file.delete(); cameraFile = null } }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { recording = false; recorder.cancel(); player.stop() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.cancel(); player.stop(); cameraFile?.delete() }
    }
    LaunchedEffect(model.uploading) { if (!model.uploading) { cameraFile?.delete(); cameraFile = null } }
    LaunchedEffect(recording) { if (recording) while (isActive) { delay(1000); seconds++ } }
    LaunchedEffect(model.messages.lastOrNull()?.id) {
        val nearBottom = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } ?: true
        if (nearBottom && model.messages.isNotEmpty()) list.animateScrollToItem(model.messages.lastIndex + if (model.hasOlder) 1 else 0)
    }
    BackHandler { model.close() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ model.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = MiyarBlue) }
            Row(Modifier.weight(1f).clickable { info = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(chat.title, 38.dp, group = chat.kind == "group")
                Column { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium); Text(if (chat.kind == "group") "${chat.participants.size} أعضاء" else chat.peer(model.me?.id.orEmpty())?.roleName ?: "مِعيار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton({ search = true }) { Icon(Icons.Default.Search, "بحث في المحادثة", tint = MiyarBlue) }
            IconButton({ info = true }) { Icon(Icons.Default.Info, "معلومات المحادثة", tint = MiyarBlue) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (model.hasOlder) item { TextButton({ model.launch { model.loadThread(true) } }, Modifier.fillMaxWidth()) { Text("رسائل أقدم") } }
            if (model.messages.isEmpty()) item { Spacer(Modifier.height(30.dp)); EmptyState("ابدئي المحادثة", "أرسلي رسالة إلى ${chat.title}") }
            itemsIndexed(model.messages, key = { _, message -> message.id }) { index, message ->
                if (index == 0 || message.createdAt - model.messages[index - 1].createdAt > 300000) Text(messageTime(message.createdAt), Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MessageBubble(message, chat, model.me?.id.orEmpty(), player, model, onOptions = { selected = message }, onPreview = { preview = it })
            }
            val typing = chat.participants.filter { it.id != model.me?.id && it.typingUntil > System.currentTimeMillis() }
            if (typing.isNotEmpty()) item { Text(typing.joinToString("، ") { it.name } + " تكتب الآن…", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        model.error?.let { error -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer), verticalAlignment = Alignment.CenterVertically) { Text(error, Modifier.weight(1f).padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall); IconButton({ model.error = null }) { Icon(Icons.Default.Close, "إغلاق") } } }
        if (model.uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!chat.canSend) Text("التواصل مع هذا الحساب موقوف", Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyMedium)
        else {
            model.reply?.let { reply -> ComposerNotice("رد على ${reply.senderName}", reply.text.ifBlank { "مرفق" }, { model.reply = null }) }
            model.attachment?.let { file -> ComposerNotice("مرفق جاهز", file.name, { model.attachment = null }) }
            if (recording) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ recording = false; recorder.cancel() }) { Icon(Icons.Default.DeleteOutline, "إلغاء التسجيل", tint = MaterialTheme.colorScheme.error) }
                    Icon(Icons.Default.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text("جارٍ التسجيل · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    FilledIconButton({ finishRecording() }) { Icon(Icons.Default.Stop, "إنهاء التسجيل") }
                }
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton({ attachMenu = true }, enabled = !model.uploading && !model.sending) { Icon(Icons.Default.AddCircle, "إرفاق", tint = MiyarBlue) }
                    DropdownMenu(attachMenu, { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("صورة من المعرض") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { attachMenu = false; photo.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        DropdownMenuItem(text = { Text("التقاط صورة") }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) }, onClick = {
                            attachMenu = false
                            try { val directory = File(context.cacheDir, "camera").apply { mkdirs() }; val file = File.createTempFile("photo-", ".jpg", directory); cameraFile = file; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
                            catch (_: Exception) { cameraFile?.delete(); cameraFile = null; model.error = "الكاميرا غير متاحة على هذا الجهاز" }
                        })
                        DropdownMenuItem(text = { Text("ملف PDF") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { attachMenu = false; document.launch(arrayOf("application/pdf")) })
                    }
                }
                TextField(model.draft, { model.changedDraft(it) }, Modifier.weight(1f), placeholder = { Text("رسالة…") }, maxLines = 5, shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (model.draft.isNotBlank() || model.attachment != null) IconButton({ model.send() }, enabled = !model.sending && !model.uploading) {
                    if (model.sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = MiyarBlue)
                } else IconButton({ if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording() else microphone.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !model.uploading) { Icon(Icons.Default.Mic, "تسجيل رسالة صوتية", tint = MiyarBlue) }
            }
        }
    }
    if (info) ConversationInfo(model, { info = false }, { info = false; search = true })
    if (search) MessageSearch(model) { search = false }
    preview?.let { AttachmentPreview(it, model) { preview = null } }
    selected?.let { message ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("خيارات الرسالة") }, text = {
            Column {
                if (!message.deleted) {
                    TextButton({ model.reply = message; selected = null }) { Icon(Icons.AutoMirrored.Filled.Reply, null); Spacer(Modifier.width(8.dp)); Text("رد") }
                    if (message.text.isNotBlank()) TextButton({ clipboard.setText(AnnotatedString(message.text)); selected = null }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("نسخ") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("👍", "❤️", "😂", "😮", "😢").forEach { value -> TextButton({ model.messageAction("react", message, value); selected = null }, contentPadding = PaddingValues(4.dp), modifier = Modifier.size(44.dp)) { Text(value) } } }
                    if (message.reactions.any { it.actorId == model.me?.id }) TextButton({ model.messageAction("react", message, ""); selected = null }) { Text("إزالة تفاعلي") }
                    if (message.senderId == model.me?.id) TextButton({ delete = message; selected = null }) { Text("حذف للجميع", color = MaterialTheme.colorScheme.error) }
                }
            }
        }, confirmButton = { TextButton({ selected = null }) { Text("إغلاق") } })
    }
    delete?.let { message -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("حذف الرسالة للجميع؟") }, confirmButton = { TextButton({ model.messageAction("deleteMessage", message); delete = null }) { Text("حذف") } }, dismissButton = { TextButton({ delete = null }) { Text("إلغاء") } }) }
}

@Composable private fun ComposerNotice(title: String, text: String, cancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelMedium, color = MiyarBlue); Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
        IconButton(cancel) { Icon(Icons.Default.Close, "إزالة") }
    }
}

@Composable private fun MessageBubble(message: Message, chat: Conversation, me: String, player: AudioPlayback, model: ChatModel, onOptions: () -> Unit, onPreview: (Attachment) -> Unit) {
    val mine = message.senderId == me
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) { Avatar(message.senderName, 28.dp); Spacer(Modifier.width(6.dp)) }
        Column(Modifier.widthIn(max = 290.dp).combinedClickable(onClick = { if (message.attachment == null) onOptions() }, onLongClick = onOptions), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && chat.kind == "group") Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = if (mine) MiyarBlue else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(if (message.kind == "image") 4.dp else 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    message.reply?.let { reply -> Surface(color = if (mine) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(8.dp)) { Text(reply.senderName, style = MaterialTheme.typography.labelSmall); Text(reply.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) } } }
                    message.attachment?.let { AttachmentContent(it, model, player, mine) { onPreview(it) } }
                    if (message.text.isNotEmpty()) SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (message.reactions.isNotEmpty()) Text(message.reactions.joinToString(" ") { it.value }, Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (mine) {
            val read = chat.participants.filter { it.id != me }.all { it.lastReadId >= message.id }
            Icon(if (read) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline, if (read) "مقروءة" else "مرسلة", Modifier.padding(start = 5.dp, bottom = 4.dp).size(14.dp), tint = MiyarBlue)
        }
    }
}
