@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tech.deanju.messenger

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun MiyarApp(model: ChatModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miyar_settings", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable { mutableStateOf(prefs.getString("appearance", "system") ?: "system") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var compose by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val snackbars = remember { SnackbarHostState() }
    MiyarTheme(appearance) {
        val lightBars = MaterialTheme.colorScheme.surface.luminanceCompat() > .5f
        SideEffect { (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, it.decorView).apply { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars } } }
        LaunchedEffect(model.me?.id, model.branch, model.conversation?.id, model.archived) {
            if (model.me == null) return@LaunchedEffect
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    try { model.refresh(); if (model.conversation != null) model.loadThread() } catch (e: Exception) { model.report(e) }
                    delay(if (model.conversation != null) 4000 else 12000)
                }
            }
        }
        LaunchedEffect(model.me?.id) { if (model.me == null) { tab = 0; compose = false } }
        LaunchedEffect(model.error, model.me) { if (model.me != null) model.error?.let { snackbars.showSnackbar(it); model.error = null } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !model.ready -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }
                model.me == null || model.changePassword -> LoginScreen(model)
                model.conversation != null -> ThreadScreen(model)
                compose -> PeopleScreen(model, true, onBack = { compose = false }, onOpen = { compose = false })
                else -> Scaffold(
                    snackbarHost = { SnackbarHost(snackbars) },
                    topBar = {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(CircleShape).clickable { tab = 2 }) { Avatar(model.me?.name.orEmpty(), 40.dp) }
                            Text(if (tab == 0) "المحادثات" else if (tab == 1) "الأشخاص" else "حسابي", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineLarge)
                            if (tab == 0) RoundIcon(if (model.archived) Icons.Default.Inbox else Icons.Default.Archive, if (model.archived) "المحادثات" else "الأرشيف") { model.toggleArchive() }
                            if (tab != 2) { Spacer(Modifier.width(8.dp)); RoundIcon(Icons.Default.Edit, "رسالة جديدة") { compose = true } }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            listOf(Triple("المحادثات", Icons.AutoMirrored.Filled.Chat, 0), Triple("الأشخاص", Icons.Default.People, 1), Triple("حسابي", Icons.Default.Person, 2)).forEach { (label, icon, index) ->
                                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MiyarBlue, selectedTextColor = MiyarBlue, indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> InboxScreen(model)
                            1 -> PeopleScreen(model, false, {}, {})
                            else -> SettingsScreen(model, appearance) { appearance = it; prefs.edit().putString("appearance", it).apply() }
                        }
                    }
                }
            }
        }
    }
}
private fun Color.luminanceCompat() = .2126f * red + .7152f * green + .0722f * blue

@Composable fun Avatar(name: String, size: Dp = 52.dp, online: Boolean = false, group: Boolean = false) {
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            if (group) Icon(Icons.Default.People, null, tint = MiyarBlue, modifier = Modifier.size(size * .48f))
            else Text(name.trim().split(" ").filter { it.isNotBlank() }.take(2).map { it.take(1) }.joinToString(""), color = MiyarBlue, fontWeight = FontWeight.Bold, fontSize = (size.value * .32f).sp)
        }
        if (online) Box(Modifier.align(Alignment.BottomEnd).size(size * .22f).clip(CircleShape).background(Color(0xFF41C657)).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
    }
}
@Composable fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Icon(icon, label, Modifier.size(23.dp)) }
}
@Composable fun SearchBox(value: String, onValue: (String) -> Unit, hint: String = "بحث") {
    TextField(value, onValue, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), placeholder = { Text(hint, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(21.dp)) }, singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}
@Composable fun EmptyState(title: String, description: String, icon: ImageVector = Icons.AutoMirrored.Filled.Chat) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(52.dp), tint = MiyarBlue)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
@Composable fun ErrorText(text: String?) { text?.let { Text(it, Modifier.fillMaxWidth().padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
fun messageTime(timestamp: Long): String = SimpleDateFormat("h:mm a", Locale.forLanguageTag("ar-JO")).format(Date(timestamp))

@Composable fun LoginScreen(model: ChatModel) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    BackHandler(model.mfa) { model.cancelMfa(); code = "" }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(58.dp))
        Box(Modifier.size(88.dp).background(MiyarBlue, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(48.dp), tint = Color.White) }
        Spacer(Modifier.height(20.dp)); Text("مِعيار", style = MaterialTheme.typography.displayLarge)
        Text("كل محادثاتك في مكان واحد", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (model.changePassword) {
                Text("تعيين كلمة مرور جديدة", style = MaterialTheme.typography.titleLarge)
                PasswordField("كلمة المرور الحالية", password, { password = it })
                PasswordField("كلمة المرور الجديدة", next, { next = it })
                PasswordField("تأكيد كلمة المرور", confirm, { confirm = it })
            } else if (model.mfa) {
                Text("التحقق من الحساب", style = MaterialTheme.typography.titleLarge)
                Text("أدخلي رمز تطبيق المصادقة أو رمز الاسترداد", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("رمز المصادقة") }, singleLine = true)
            } else {
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم أو رقم الطالبة") }, singleLine = true, leadingIcon = { Icon(Icons.Default.PersonOutline, null) }, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                PasswordField("كلمة المرور", password, { password = it })
            }
            ErrorText(model.error)
            if (model.retrySession) TextButton(onClick = { model.retry() }, enabled = !model.busy) { Text("إعادة الاتصال بالحساب المحفوظ") }
            val enabled = !model.busy && when { model.changePassword -> password.isNotEmpty() && next.length >= 10 && next == confirm; model.mfa -> code.isNotBlank(); else -> username.isNotBlank() && password.isNotEmpty() }
            Button(onClick = { if (model.changePassword) model.setPassword(password, next, confirm) else model.login(username, password, code) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                if (model.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (model.changePassword) "حفظ ومتابعة" else if (model.mfa) "تحقق ومتابعة" else "تسجيل الدخول", fontSize = 16.sp)
            }
            if (model.mfa) TextButton({ model.cancelMfa(); code = "" }, Modifier.fillMaxWidth()) { Text("العودة لتسجيل الدخول") }
        }
        Spacer(Modifier.height(24.dp)); Text("استخدمي بيانات حسابك في منصة معيار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ uri.openUri("https://deanju.tech") }) { Text("فتح منصة معيار") }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun PasswordField(label: String, value: String, onValue: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور") } })
}

@Composable fun InboxScreen(model: ChatModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var online by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(model.branch) { try { online = model.contacts("").contacts.filter { it.online } } catch (e: Exception) { if (e is CancellationException) throw e } }
    val filtered = model.conversations.filter { search.isBlank() || it.title.contains(search, true) || it.lastMessage?.text?.contains(search, true) == true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { SearchBox(search, { search = it }, "بحث في المحادثات") }
        if (model.archived) item { Text("المحادثات المؤرشفة", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
        if (online.isNotEmpty() && search.isBlank() && !model.archived) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(online, key = { it.id }) { person -> Column(Modifier.width(62.dp).clickable { model.launch { model.create(person) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(person.name, 52.dp, true); Spacer(Modifier.height(5.dp)); Text(person.name.substringBefore(" "), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
        if (filtered.isEmpty()) item { Spacer(Modifier.height(45.dp)); EmptyState(if (search.isBlank()) "لا توجد محادثات" else "لا توجد نتائج", if (search.isBlank()) "ابدئي رسالة جديدة من الزر في الأعلى" else "جرّبي اسمًا أو كلمة أخرى") }
        items(filtered, key = { it.id }) { chat ->
            Row(Modifier.fillMaxWidth().clickable { model.open(chat) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(chat.title, 60.dp, group = chat.kind == "group")
                Column(Modifier.weight(1f)) {
                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(chat.lastMessage?.let { (if (it.senderId == model.me?.id) "أنتِ: " else "") + it.text } ?: "ابدئي المحادثة", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    chat.lastMessage?.let { Text(messageTime(it.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (chat.unread > 0) Badge(containerColor = MiyarBlue) { Text(if (chat.unread > 99) "99+" else "${chat.unread}", color = Color.White) }
                    else if (chat.muted) Icon(Icons.Default.NotificationsOff, "مكتومة", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
