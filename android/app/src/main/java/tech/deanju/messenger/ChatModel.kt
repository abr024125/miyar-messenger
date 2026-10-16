package tech.deanju.messenger

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class ChatModel(application: Application) : AndroidViewModel(application) {
    val api = MessengerApi(application)
    var me by mutableStateOf<MessengerUser?>(null); private set
    var branch by mutableStateOf<Int?>(null); private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var conversation by mutableStateOf<Conversation?>(null); private set
    var messages by mutableStateOf<List<Message>>(emptyList()); private set
    var hasOlder by mutableStateOf(false); private set
    var archived by mutableStateOf(false); private set
    var showOnline by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var sending by mutableStateOf(false); private set
    var uploading by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var mfa by mutableStateOf(false); private set
    var changePassword by mutableStateOf(false); private set
    var retrySession by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var draft by mutableStateOf("")
    var reply by mutableStateOf<Message?>(null)
    var attachment by mutableStateOf<Attachment?>(null)
    private var revision = 0
    private var lastRead = 0L
    private var lastTyped = 0L
    private var retryFingerprint: String? = null
    private var retryId: String? = null
    private val threadLock = Mutex()
    private var openJob: Job? = null
    private var uploadJob: Job? = null

    init { viewModelScope.launch { if (api.vault.hasSession()) { try { refresh() } catch (e: Exception) { handle(e); retrySession = e !is ApiFailure || e.status !in setOf(401, 428) } }; ready = true } }
    private fun handle(e: Exception) {
        if (e is CancellationException) throw e
        if (e is ApiFailure && e.status == 401) { revision++; me = null; branch = null; conversations = emptyList(); close(); api.clear(); retrySession = false }
        if (e is ApiFailure && e.status == 428) changePassword = true
        error = when (e) { is ApiFailure, is IllegalArgumentException -> e.message; is java.io.IOException -> "تعذر الاتصال. تحققي من الإنترنت وحاولي مجددًا"; else -> "تعذر إتمام العملية. حاولي مجددًا" }
    }
    fun launch(action: suspend () -> Unit) = viewModelScope.launch { try { action() } catch (e: Exception) { handle(e) } }
    fun report(e: Exception) = handle(e)
    suspend fun refresh() {
        val epoch = revision; val selected = branch; val requestedArchive = archived
        val result: Inbox = api.get(buildMap { put("archived", if (requestedArchive) "1" else "0"); selected?.let { put("branch", "$it") } })
        if (epoch != revision || selected != branch || requestedArchive != archived) return
        me = result.me; branch = result.me.branchId; conversations = result.conversations; showOnline = result.settings.showOnline; retrySession = false
    }
    fun login(username: String, password: String, code: String) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try {
                val result = if (mfa) api.system(PlatformAction.MFA, buildJsonObject { put("code", code) })
                    else api.system(PlatformAction.LOGIN, buildJsonObject { put("username", username.trim()); put("password", password) })
                mfa = result.mfaRequired
                if (!mfa) { changePassword = result.user?.requiresPasswordChange == true; if (!changePassword) refresh() }
            } catch (e: Exception) {
                if (mfa && e is ApiFailure && e.status == 401) error = e.message else handle(e)
            } finally { busy = false }
        }
    }
    fun setPassword(current: String, password: String, confirmation: String) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try {
            api.system(PlatformAction.CHANGE_PASSWORD, buildJsonObject { put("currentPassword", current); put("password", password); put("confirmation", confirmation) })
            changePassword = false; refresh()
        } catch (e: Exception) { handle(e) } finally { busy = false } }
    }
    fun cancelMfa() { mfa = false; error = null; api.clear() }
    fun retry() { if (busy) return; busy = true; launch { try { refresh() } finally { busy = false } } }
    fun logout() {
        if (busy) return
        revision++; me = null; branch = null; conversations = emptyList(); close(); busy = true
        viewModelScope.launch {
            try { api.system(PlatformAction.LOGOUT) } catch (_: Exception) {} finally {
                api.clear(); mfa = false; changePassword = false; retrySession = false; archived = false; error = null; busy = false
            }
        }
    }
    fun selectBranch(id: Int) {
        if (id == branch) return
        revision++; close(); conversations = emptyList(); branch = id; archived = false
        launch { refresh() }
    }
    fun toggleArchive() { archived = !archived; conversations = emptyList(); launch { refresh() } }
    suspend fun contacts(query: String, page: Int = 1): Contacts {
        val id = branch ?: throw ApiFailure(401, "سجلي الدخول أولًا")
        return api.get(mapOf("view" to "contacts", "q" to query, "page" to "$page", "branch" to "$id"))
    }
    suspend fun create(person: Person): Conversation {
        val selected = branch; val epoch = revision
        val result: NewConversation = api.post(buildJsonObject { put("action", "direct"); put("recipientId", person.id) }, selected)
        if (epoch != revision || selected != branch) throw CancellationException()
        refresh(); open(result.conversation); return result.conversation
    }
    suspend fun group(title: String, people: Set<String>) {
        val selected = branch; val epoch = revision
        val result: NewConversation = api.post(buildJsonObject { put("action", "group"); put("title", title.trim()); putJsonArray("participantIds") { people.forEach { add(it) } } }, selected)
        if (epoch != revision || selected != branch) throw CancellationException()
        refresh(); open(result.conversation)
    }
    fun open(value: Conversation) {
        close(); conversation = value; error = null
        openJob = launch { loadThread() }
    }
    fun close() {
        openJob?.cancel(); uploadJob?.cancel(); conversation = null; messages = emptyList(); hasOlder = false
        draft = ""; reply = null; attachment = null; lastRead = 0; retryId = null; retryFingerprint = null; uploading = false
    }
    suspend fun action(name: String, values: JsonObject = buildJsonObject {}, chat: Conversation? = conversation) {
        val body = JsonObject(values + buildJsonObject { put("action", name); chat?.let { put("conversationId", it.id) } })
        api.post<JsonObject>(body, branch)
    }
    suspend fun loadThread(older: Boolean = false) = threadLock.withLock {
        val current = conversation ?: return@withLock
        val selected = branch ?: return@withLock
        val epoch = revision
        val result: Thread = api.get(buildMap { put("conversation", current.id); put("branch", "$selected"); if (older) messages.firstOrNull()?.let { put("before", "${it.id}") } })
        if (revision != epoch || branch != selected || conversation?.id != current.id) return@withLock
        conversation = result.conversation; messages = mergeMessages(messages, result.messages)
        if (older || messages.size <= 50) hasOlder = result.hasMore
        val last = messages.lastOrNull()?.id ?: 0
        if (!older && last > lastRead) { action("read", buildJsonObject { put("lastReadId", last) }, result.conversation); lastRead = last }
    }
    fun changedDraft(value: String) {
        draft = value.take(5000)
        if (conversation == null || System.currentTimeMillis() - lastTyped < 4000) return
        lastTyped = System.currentTimeMillis()
        launch { action("typing", buildJsonObject { put("active", value.isNotBlank()) }) }
    }
    fun send() {
        val chat = conversation ?: return; val selected = branch ?: return; val epoch = revision
        if (sending || uploading || !chat.canSend || (draft.isBlank() && attachment == null)) return
        val content = draft.trim(); val uploaded = attachment; val replying = reply
        val fingerprint = "$content|${uploaded?.id.orEmpty()}|${replying?.id ?: 0}"
        if (retryFingerprint != fingerprint) { retryFingerprint = fingerprint; retryId = UUID.randomUUID().toString() }
        val clientId = retryId!!; sending = true; error = null
        viewModelScope.launch { try {
            api.post<JsonObject>(buildJsonObject { put("action", "send"); put("conversationId", chat.id); put("clientId", clientId); put("text", content); uploaded?.let { put("attachmentId", it.id) }; replying?.let { put("replyTo", it.id) } }, selected)
            if (epoch == revision && conversation?.id == chat.id) {
                if (draft.trim() == content) draft = ""; if (attachment?.id == uploaded?.id) attachment = null; if (reply?.id == replying?.id) reply = null
                retryFingerprint = null; retryId = null; loadThread(); refresh()
            }
        } catch (e: Exception) { handle(e) } finally { sending = false } }
    }
    fun attach(uri: Uri) {
        val chat = conversation ?: return; val selected = branch ?: return; val epoch = revision
        if (uploading) return
        uploading = true; error = null
        uploadJob = viewModelScope.launch { try {
            val picked = MediaTools.read(getApplication(), uri)
            val result = api.upload(picked.bytes, picked.name, picked.mime, chat.id, selected)
            if (epoch == revision && conversation?.id == chat.id) attachment = result
        } catch (e: Exception) { handle(e) } finally { uploading = false } }
    }
    fun recordFile(file: java.io.File) {
        val chat = conversation ?: return; val selected = branch ?: return; val epoch = revision
        if (uploading) { file.delete(); return }
        uploading = true
        uploadJob = viewModelScope.launch { try {
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val result = api.upload(bytes, "تسجيل صوتي.m4a", "audio/mp4", chat.id, selected)
            if (epoch == revision && conversation?.id == chat.id) attachment = result
        } catch (e: Exception) { handle(e) } finally { file.delete(); uploading = false } }
    }
    fun messageAction(name: String, message: Message, value: String? = null) = launch {
        action(name, buildJsonObject { put("messageId", message.id); value?.let { put("value", it) } }); loadThread()
    }
    fun preference(name: String, value: Boolean) = launch { action("preferences", buildJsonObject { put(name, value) }); loadThread(); refresh() }
    fun presence(value: Boolean) = launch { action("profile", buildJsonObject { put("showOnline", value) }); showOnline = value }
}
