package tech.deanju.messenger

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object RequestContracts {
    val origin = "https://deanju.tech".toHttpUrl()
    private val paths = setOf("/api/system", "/api/messenger", "/api/messenger/media")
    fun url(path: String = "/api/messenger", query: Map<String, String> = emptyMap()): HttpUrl {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains('\\') && !path.contains('#')) { "رابط الخدمة غير صالح" }
        val result = origin.resolve(path) ?: throw IllegalArgumentException("رابط الخدمة غير صالح")
        require(result.scheme == "https" && result.host == origin.host && result.port == origin.port && result.encodedPath in paths) { "رابط الخدمة غير صالح" }
        return result.newBuilder().apply { query.toSortedMap().forEach { (key, value) -> addQueryParameter(key, value) } }.build()
    }
    fun systemBody(action: PlatformAction, values: JsonObject = buildJsonObject {}): JsonObject =
        JsonObject(values + ("action" to JsonPrimitive(action.wireName)))
    fun jsonRequest(body: JsonObject, branch: Int? = null, path: String = "/api/messenger"): Request {
        val bytes = WireJson.encodeToString(body).encodeToByteArray()
        require(bytes.size < 24000) { "الطلب كبير جدًا" }
        return Request.Builder().url(url(path, branch?.let { mapOf("branch" to "$it") } ?: emptyMap()))
            .header("Origin", origin.toString().removeSuffix("/")).post(bytes.toRequestBody("application/json".toMediaType())).build()
    }
}

class MessengerApi(context: Context) {
    val vault = SessionVault(context)
    private val cache = File(context.cacheDir, "messages")
    private val client = OkHttpClient.Builder().cookieJar(vault).followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    private suspend fun response(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
    private suspend fun execute(request: Request, limit: Int = 2 * 1024 * 1024): ByteArray = withContext(Dispatchers.IO) {
        response(request).use { response ->
            val input = response.body?.byteStream() ?: throw ApiFailure(503, "تعذر الاتصال بالخادم")
            val data = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break; if (output.size() + n > limit) throw ApiFailure(413, "الملف أكبر من الحجم المسموح"); output.write(buffer, 0, n) }
                output.toByteArray()
            }
            if (!response.isSuccessful) {
                val message = runCatching { WireJson.parseToJsonElement(data.decodeToString()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                throw ApiFailure(response.code, message ?: "تعذر إتمام الطلب")
            }
            data
        }
    }
    suspend inline fun <reified T> get(query: Map<String, String> = emptyMap()): T = WireJson.decodeFromString(getRaw(query))
    suspend fun getRaw(query: Map<String, String>) = execute(Request.Builder().url(RequestContracts.url(query = query)).build()).decodeToString()
    suspend inline fun <reified T> post(body: JsonObject, branch: Int? = null, path: String = "/api/messenger"): T = WireJson.decodeFromString(postRaw(body, branch, path))
    suspend fun postRaw(body: JsonObject, branch: Int?, path: String): String {
        return execute(RequestContracts.jsonRequest(body, branch, path)).decodeToString()
    }
    suspend fun system(action: PlatformAction, values: JsonObject = buildJsonObject {}): Login =
        post(RequestContracts.systemBody(action, values), path = "/api/system")
    suspend fun upload(data: ByteArray, name: String, mime: String, conversation: String, branch: Int): Attachment {
        require(data.isNotEmpty() && data.size <= 10 * 1024 * 1024) { "الحد الأعلى للمرفق 10 ميجابايت" }
        val request = Request.Builder().url(RequestContracts.url("/api/messenger/media", mapOf("name" to name, "conversation" to conversation, "branch" to "$branch")))
            .header("Origin", RequestContracts.origin.toString().removeSuffix("/")).post(data.toRequestBody(mime.toMediaType())).build()
        return WireJson.decodeFromString<Upload>(execute(request).decodeToString()).attachment
    }
    suspend fun file(attachment: Attachment): File = withContext(Dispatchers.IO) {
        require(attachment.id.matches(Regex("[a-fA-F0-9-]{36}")))
        cache.mkdirs()
        val file = File(cache, attachment.id)
        if (!file.exists()) {
            val bytes = execute(Request.Builder().url(RequestContracts.url(attachment.url)).build(), 10 * 1024 * 1024)
            val pending = File(cache, "${attachment.id}.pending")
            try { pending.writeBytes(bytes); check(pending.renameTo(file)) } finally { pending.delete() }
        }
        file
    }
    fun clear() { client.dispatcher.cancelAll(); vault.clear(); cache.deleteRecursively() }
}
