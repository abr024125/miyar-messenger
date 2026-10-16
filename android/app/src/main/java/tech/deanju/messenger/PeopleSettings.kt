@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tech.deanju.messenger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun PeopleScreen(model: ChatModel, creating: Boolean, onBack: () -> Unit, onOpen: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var more by remember { mutableStateOf(false) }; var page by remember { mutableIntStateOf(1) }
    var group by rememberSaveable { mutableStateOf(false) }; var title by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, model.branch) {
        delay(250); loading = true; error = null; page = 1
        try { val result = model.contacts(query); people = result.contacts; more = result.hasMore }
        catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "تعذر تحميل الأشخاص" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().then(if (creating) Modifier.safeDrawingPadding() else Modifier)) {
        if (creating) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text(if (group) "مجموعة جديدة" else "رسالة جديدة", style = MaterialTheme.typography.titleLarge)
        }
        if (creating && model.me?.role != "student") Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("إنشاء مجموعة", Modifier.weight(1f)); Switch(group, { group = it; selected = emptySet() })
        }
        if (group) OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), label = { Text("اسم المجموعة") }, singleLine = true)
        SearchBox(query, { query = it }, "بحث عن شخص")
        ErrorText(error)
        LazyColumn(Modifier.weight(1f)) {
            if (people.isEmpty() && !loading) item { EmptyState("لا توجد نتائج", "تظهر هنا الحسابات المتاحة لك حسب صلاحيات المنصة", Icons.Default.PeopleOutline) }
            items(people, key = { it.id }) { person ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !saving) {
                    if (group) {
                        if (person.id in selected) selected -= person.id else if (selected.size < 31) selected += person.id
                    } else scope.launch { saving = true; error = null; try { model.create(person); onOpen() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "تعذر فتح المحادثة" } finally { saving = false } }
                }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(person.name, 52.dp, person.online)
                    Column(Modifier.weight(1f)) { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium); Text(person.roleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (group) Checkbox(person.id in selected, onCheckedChange = null)
                }
            }
            if (more) item { TextButton({ if (!loading) scope.launch {
                val requested = query; loading = true
                try { val result = model.contacts(requested, page + 1); if (query == requested) { people = (people + result.contacts).distinctBy { it.id }; more = result.hasMore; page++ } }
                catch (e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { loading = false }
            } }, Modifier.fillMaxWidth(), enabled = !loading) { Text("عرض المزيد") } }
            if (loading || saving) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)) }
        }
        if (group) Button({ scope.launch { saving = true; error = null; try { model.group(title, selected); onOpen() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { saving = false } } },
            Modifier.fillMaxWidth().padding(16.dp).height(52.dp), enabled = title.isNotBlank() && selected.isNotEmpty() && !saving) { Text("إنشاء المجموعة · ${selected.size}") }
    }
}

@Composable fun SettingsScreen(model: ChatModel, appearance: String, onAppearance: (String) -> Unit) {
    var branches by remember { mutableStateOf(false) }; var logout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(model.me?.name.orEmpty(), 92.dp); Spacer(Modifier.height(12.dp)); Text(model.me?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            Text(model.me?.branchName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Text("المظهر", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "حسب الجهاز", "light" to "فاتح", "dark" to "داكن").forEach { (key, label) -> FilterChip(appearance == key, { onAppearance(key) }, { Text(label) }) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Visibility, null); Text("إظهار حالة الاتصال", Modifier.weight(1f).padding(horizontal = 12.dp)); Switch(model.showOnline, { model.presence(it) }) }
        model.me?.let { me ->
            if (me.branches.size > 1) Box {
                OutlinedButton({ branches = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Business, null); Text(me.branchName, Modifier.weight(1f).padding(8.dp)); Icon(Icons.Default.ExpandMore, null) }
                DropdownMenu(branches, { branches = false }) { me.branches.forEach { branch -> DropdownMenuItem(text = { Text(branch.name + if (branch.status == "active") "" else " · للقراءة") }, onClick = { branches = false; model.selectBranch(branch.id) }) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("رقم الحساب"); Text(me.id.substringAfter(':'), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalDivider()
        Text("يستخدم التطبيق حساب معيار وصلاحيات فرعك. تُحفظ جلسة الدخول مشفّرة على هذا الجهاز.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ logout = true }, Modifier.fillMaxWidth(), enabled = !model.busy) { Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(10.dp)); Text("تسجيل الخروج") }
        Text("مِعيار · ${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("تسجيل الخروج؟") }, text = { Text("يمكنك الدخول مجددًا ببيانات حساب معيار") }, confirmButton = { TextButton({ logout = false; model.logout() }) { Text("تسجيل الخروج") } }, dismissButton = { TextButton({ logout = false }) { Text("إلغاء") } })
}
