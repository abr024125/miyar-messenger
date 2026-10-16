@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tech.deanju.messenger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun ConversationInfo(model: ChatModel, dismiss: () -> Unit, onSearch: () -> Unit) {
    val chat = model.conversation ?: return
    var title by remember(chat.id) { mutableStateOf(chat.title) }
    var confirm by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    fun update(name: String, values: JsonObject = buildJsonObject {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { model.action(name, values); if (name == "leave") model.close() else model.loadThread(); model.refresh(); dismiss() }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "تعذر حفظ الإعداد" }
            finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Avatar(chat.title, 86.dp, group = chat.kind == "group"); Spacer(Modifier.height(12.dp)); Text(chat.title, style = MaterialTheme.typography.headlineSmall) }
            TextButton(onSearch, Modifier.fillMaxWidth()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("بحث في المحادثة") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("كتم المحادثة", Modifier.weight(1f)); Switch(chat.muted, { update("preferences", buildJsonObject { put("muted", it) }) }, enabled = !busy) }
            OutlinedButton({ update("preferences", buildJsonObject { put("archived", !chat.archived) }) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (chat.archived) "إلغاء الأرشفة" else "أرشفة المحادثة") }
            if (chat.kind == "group" && chat.createdBy == model.me?.id) {
                OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") }, singleLine = true)
                TextButton({ update("rename", buildJsonObject { put("title", title.trim()) }) }, enabled = !busy && title.isNotBlank() && title != chat.title) { Text("حفظ الاسم") }
            }
            HorizontalDivider(); Text("الأعضاء", style = MaterialTheme.typography.titleSmall)
            chat.participants.forEach { person -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Avatar(person.name, 36.dp); Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(person.roleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            HorizontalDivider()
            if (chat.kind == "direct") {
                TextButton({ confirm = "block" }, enabled = !busy) { Text("حظر الحساب", color = MaterialTheme.colorScheme.error) }
                if (!chat.canSend) TextButton({ chat.peer(model.me?.id.orEmpty())?.let { update("block", buildJsonObject { put("participantId", it.id); put("blocked", false) }) } }, enabled = !busy) { Text("إلغاء الحظر من جهتي") }
            } else TextButton({ confirm = "leave" }, enabled = !busy) { Text("مغادرة المجموعة", color = MaterialTheme.colorScheme.error) }
            ErrorText(error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" }, title = { Text(if (confirm == "block") "حظر الحساب؟" else "مغادرة المجموعة؟") },
        confirmButton = { TextButton({ val action = confirm; confirm = ""; if (action == "leave") update("leave") else chat.peer(model.me?.id.orEmpty())?.let { update("block", buildJsonObject { put("participantId", it.id); put("blocked", true) }) } }) { Text("تأكيد") } }, dismissButton = { TextButton({ confirm = "" }) { Text("إلغاء") } })
}

@Composable fun MessageSearch(model: ChatModel, dismiss: () -> Unit) {
    val chat = model.conversation ?: return
    var query by remember { mutableStateOf("") }; var found by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) { found = emptyList(); return@LaunchedEffect }
        delay(300); loading = true; error = null
        try { val result: Thread = model.api.get(mapOf("conversation" to chat.id, "branch" to "${model.branch}", "q" to query)); found = result.messages }
        catch (e: Exception) { if (e is CancellationException) throw e; error = e.message }
        finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxHeight(.8f)) {
            Text("بحث في المحادثة", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
            SearchBox(query, { query = it }, "كلمة في الرسائل")
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorText(error)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!loading && query.isNotBlank() && found.isEmpty()) item { Text("لا توجد رسائل مطابقة", style = MaterialTheme.typography.bodyMedium) }
                items(found, key = { it.id }) { message -> Column { Text(message.senderName, style = MaterialTheme.typography.titleSmall); Text(message.text, style = MaterialTheme.typography.bodyLarge); Text(messageTime(message.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.padding(top = 10.dp)) } }
            }
        }
    }
}
