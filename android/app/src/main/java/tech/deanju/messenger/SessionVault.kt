package tech.deanju.messenger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Only encrypted cookies are persisted; passwords and message bodies are never stored. */
class SessionVault(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("miyar_session", Context.MODE_PRIVATE)
    private val origin = RequestContracts.origin
    private var cookies: List<Cookie> = restore()
    private val alias = "miyar.messenger.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("miyar.messenger.session.v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("miyar.messenger.session.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun restore(): List<Cookie> = runCatching {
        val encoded = prefs.getString("cookies", null) ?: return emptyList()
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val strings = WireJson.decodeFromString(ListSerializer(String.serializer()), cipher.doFinal(packed.copyOfRange(12, packed.size)).decodeToString())
        strings.mapNotNull { Cookie.parse(origin, it) }.filter { it.secure && it.matches(origin) && it.expiresAt > System.currentTimeMillis() }
    }.getOrElse { prefs.edit().clear().apply(); emptyList() }
    @Synchronized override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
        if (url.host != origin.host || !url.isHttps) return
        val allowed = received.filter { it.secure && (it.domain == origin.host) }
        val replaced = allowed.map { "${it.name}|${it.domain}|${it.path}" }.toSet()
        cookies = (cookies.filterNot { "${it.name}|${it.domain}|${it.path}" in replaced } + allowed)
            .filter { it.expiresAt > System.currentTimeMillis() }
        if (cookies.isEmpty()) { prefs.edit().clear().apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plain = WireJson.encodeToString(ListSerializer(String.serializer()), cookies.map { it.toString() }).encodeToByteArray()
        prefs.edit().putString("cookies", Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)).apply()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host == origin.host && url.isHttps) cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() } else emptyList()
    @Synchronized fun hasSession() = cookies.any { it.expiresAt > System.currentTimeMillis() }
    @Synchronized fun clear() { cookies = emptyList(); prefs.edit().clear().apply() }
}
