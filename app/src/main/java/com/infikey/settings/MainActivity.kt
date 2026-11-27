package com.infikey.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infikey.Global
import com.infikey.ai.AiRouter
import com.infikey.ai.CloudEndpoint
import com.infikey.ai.DefaultModel
import com.infikey.ai.EndpointStore
import com.infikey.layout.Layouts
import com.infikey.theme.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * InfiKey settings — single activity, six screens:
 * Home · Languages · AI · Look · Features · Advanced.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Global.init(applicationContext as com.infikey.InfiKeyApp)
        setContent { InfiKeySettingsApp() }
    }
}

// ---------- palette (mirrors ThemeEngine defaults) ----------

private val Ink = Color(0xFF101014)
private val Surface = Color(0xFF16161D)
private val Card = Color(0xFF1C1C26)
private val CardSpecial = Color(0xFF23232F)
private val TextPrimary = Color(0xFFF4F4FA)
private val TextHint = Color(0xFF8A8A9A)
private val Accent = Color(0xFF8B5CF6)
private val Accent2 = Color(0xFF34D399)

private enum class Screen(val label: String) {
    HOME("Home"), LANGUAGES("Languages"), AI("AI"), LOOK("Look"), FEATURES("Features"), ADVANCED("Advanced")
}

@Composable
private fun InfiKeySettingsApp() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }

    // biometric lock (#62): gate the whole settings app; tap retries after cancel
    var unlocked by remember { mutableStateOf(!Global.prefs.biometricLock) }
    var unlockAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(unlockAttempt) { if (!unlocked) unlockWithBiometric(ctx) { unlocked = true } }
    if (!unlocked) {
        Box(Modifier.fillMaxSize().clickable { unlockAttempt++ }, contentAlignment = Alignment.Center) {
            Text("🔒 InfiKey locked — unlock to open settings\n(tap to try again)", color = TextHint)
        }
        return
    }
    Surface(color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 20.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u2726 InfiKey", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(screen.label, color = TextHint, fontSize = 15.sp)
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Screen.entries.forEach { s ->
                    TabChip(s.label, s == screen) { screen = s }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen()
                    Screen.LANGUAGES -> LanguagesScreen()
                    Screen.AI -> AiScreen()
                    Screen.LOOK -> LookScreen()
                    Screen.FEATURES -> FeaturesScreen()
                    Screen.ADVANCED -> AdvancedScreen()
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, onTap: () -> Unit) {
    Text(
        label,
        color = if (active) Color.White else TextHint,
        fontSize = 13.sp,
        modifier = Modifier
            .clipChip(if (active) Accent else Card)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

private fun Modifier.clipChip(color: Color) = this.then(
    Modifier.background(color, RoundedCornerShape(50))
)

// ---------- shared pieces ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = TextHint,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun RowCard(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onTap: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(Card, RoundedCornerShape(14.dp))
            .clickable(enabled = onTap != null) { onTap?.invoke() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = TextHint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchPref(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowCard(title, subtitle, trailing = {
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Accent
        ))
    })
}

// ---------- HOME ----------

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val imeEnabled = remember { mutableStateOf(isImeEnabled(ctx)) }
    val imeSelected = remember { mutableStateOf(isImeSelected(ctx)) }
    val modelLoaded = remember { mutableStateOf(runCatching { Global.router.isModelLoaded() }.getOrDefault(false)) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle("Setup")
            RowCard(
                "1 · Enable InfiKey in system settings",
                if (imeEnabled.value) "✓ Enabled" else "Tap to open system keyboard settings",
                onTap = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                        .onFailure { toast(ctx, "Cannot open settings") }
                }
            )
            RowCard(
                "2 · Switch to InfiKey",
                if (imeSelected.value) "✓ Active keyboard" else "Open the input-method picker",
                onTap = {
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                    if (imm != null) imm.showInputMethodPicker() else toast(ctx, "Long-press any text field → 'Change keyboard'")
                }
            )
            RowCard(
                "AI model status",
                if (modelLoaded.value) "✓ On-device model loaded" else "No model loaded — go to the AI tab",
                onTap = null
            )
        }
        item {
            SectionTitle("What InfiKey can do")
            listOf(
                "\uD83E\uDDE0 On-device AI — LFM2.5-1.2B or ANY .gguf you import, runs offline via llama.cpp",
                "\u2601 Any cloud API — any OpenAI-compatible URL, any model, custom headers",
                "\u270D\uFE0F 12 scripts — Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi, Odia, Urdu, Santali + Latin",
                "\u2728 85 features — glide, popups, clipboard history, macros, per-app memory, theme codes…"
            ).forEach { line ->
                RowCard(line)
            }
        }
    }
}

private fun isImeEnabled(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        ?.split(":")?.any { it.contains("com.infikey") } ?: false

private fun isImeSelected(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.contains("com.infikey") == true

private fun openImePicker(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent("android.settings.INPUT_METHOD_SUBTYPE_SETTINGS"))
    }.onFailure { toast(ctx, "Long-press any text field → 'Change keyboard'") }
}

private fun toast(ctx: Context, msg: String) =
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ---------- LANGUAGES ----------

@Composable
private fun LanguagesScreen() {
    val enabled = remember { mutableStateOf(Global.prefs.enabledLayouts()) }
    val active = remember { mutableStateOf(Global.prefs.activeLayoutId) }
    val translit = remember { mutableStateOf(Global.prefs.translitLang) }

    val allLayouts = Layouts.ALL
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle("Your keyboards (tap to activate · check to keep)")
            RowCard(
                "Transliteration",
                "Type 'namaste' → get नमस्ते — " + when (translit.value) {
                    "" -> "off"
                    else -> Layouts.byId(translit.value).name
                },
                trailing = {
                    Text(if (translit.value.isEmpty()) "off" else "on", color = Accent2, fontSize = 13.sp)
                },
                onTap = {
                    val chain = listOf("") + com.infikey.layout.Transliteration.schemes().map { it.id }
                    translit.value = chain[(chain.indexOf(translit.value) + 1).mod(chain.size)]
                    Global.prefs.translitLang = translit.value
                }
            )
        }
        items(allLayouts) { layout ->
            val checked = layout.id in enabled.value
            RowCard(
                layout.name,
                "script: ${layout.script}${if (layout.rtl) " · RTL" else ""}",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active.value == layout.id) Text("active", color = Accent2, fontSize = 12.sp)
                        Checkbox(checked = checked, onCheckedChange = { on ->
                            val set = enabled.value.toMutableSet()
                            if (on) set.add(layout.id) else if (set.size > 1) set.remove(layout.id)
                            enabled.value = set
                            Global.prefs.setEnabledLayouts(set)
                        }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                    }
                },
                onTap = {
                    active.value = layout.id
                    Global.prefs.activeLayoutId = layout.id
                    Global.suggestions.loadForLayout(layout.id)
                }
            )
        }
    }
}

// ---------- AI ----------

@Composable
private fun AiScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = Global.prefs
    val models = remember { mutableStateOf(Global.models.scan()) }
    val loadedPath = remember { mutableStateOf(Global.router.currentModelPath) }
    val downloading = remember { mutableStateOf(false) }
    val progress = remember { mutableStateOf(0f) }
    val endpoints = remember { mutableStateOf(loadEndpoints()) }
    // API key editor (#55): null = closed, CloudEndpoint() placeholder = add,
    // existing endpoint = edit (key is re-encrypted on save)
    var addEndpointOpen by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableStateOf<CloudEndpoint?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                toast(ctx, "Importing…")
                val result = withContext(Dispatchers.IO) { Global.models.importFromUri(uri) }
                result.onSuccess {
                    models.value = Global.models.scan()
                    toast(ctx, "Imported ${it.name}")
                }.onFailure { toast(ctx, "Import failed: ${it.message}") }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle("On-device model (runs offline)")
            if (downloading.value) {
                RowCard("Downloading ${DefaultModel.FILE}…", "${(progress.value * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = Accent,
                    trackColor = Card
                )
            }
            RowCard(
                "Download default model",
                "${DefaultModel.REPO} · Q4_K_M · ~731 MB",
                onTap = {
                    if (downloading.value) return@RowCard
                    downloading.value = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            Global.models.downloadDefault { done, total ->
                                if (total > 0) progress.value = done.toFloat() / total
                            }
                        }
                        downloading.value = false
                        r.onSuccess {
                            models.value = Global.models.scan()
                            toast(ctx, "Downloaded! Tap it below to load.")
                        }.onFailure { toast(ctx, "Download failed: ${it.message}") }
                    }
                }
            )
            if (Global.prefs.feature("any_gguf")) {
                RowCard("Import any .gguf from storage", "Local, OpenRouter-style GGUFs, anything — InfiKey validates and loads it", onTap = {
                    importLauncher.launch(arrayOf("*/*"))
                })
            }
        }
        items(models.value) { m ->
            RowCard(
                m.name,
                "${m.sizeLabel()}${if (m.file.absolutePath == loadedPath.value) " · LOADED" else ""}" +
                    (if (m.isDefault) " · default" else ""),
                trailing = {
                    Text("Load", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                },
                onTap = {
                    scope.launch {
                        toast(ctx, "Loading ${m.name}…")
                        val ok = Global.router.loadOnDevice(m.file.absolutePath)
                        if (ok) {
                            loadedPath.value = Global.router.currentModelPath
                            toast(ctx, "Loaded ✓")
                        } else toast(ctx, "Load failed — try a smaller model")
                    }
                }
            )
        }
        item {
            SectionTitle("Cloud APIs (any OpenAI-compatible site)")
            RowCard("AI mode", "on-device ↔ cloud ↔ offline · current: ${prefs.aiMode}", trailing = {
                Text(
                    when (prefs.aiMode) { "auto" -> "auto"; "cloud" -> "cloud"; else -> "offline" },
                    color = Accent2, fontSize = 13.sp
                )
            }, onTap = {
                prefs.aiMode = when (prefs.aiMode) { "auto" -> "cloud"; "cloud" -> "offline"; else -> "auto" }
            })
        }
        if (Global.prefs.feature("cloud_any")) {
            items(endpoints.value) { e ->
                RowCard(
                    "${e.name} — ${e.model}",
                    e.baseUrl + if (e.apiKey.isBlank()) " · no API key yet" else "",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.id == prefs.activeCloudEndpointId)
                                Text("active", color = Accent2, fontSize = 12.sp)
                            Text("Edit", color = Accent, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 10.dp).clickable { editingEndpoint = e })
                            Text("\u2715", color = TextHint, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 10.dp).clickable {
                                    endpointStore().remove(e.id)
                                    endpoints.value = loadEndpoints()
                                    toast(ctx, "Endpoint removed")
                                })
                        }
                    },
                    onTap = {
                        prefs.activeCloudEndpointId = e.id
                        Global.router.setDeepCloud(e.id)
                        toast(ctx, "${e.name} is now active")
                    }
                )
            }
            item {
                RowCard("+ Add cloud endpoint", "Groq · OpenRouter · Ollama · LM Studio · your server — any link works", onTap = {
                    addEndpointOpen = true
                })
            }
        }
    }

    // add / edit dialog — the ONLY place an API key can be entered
    if (addEndpointOpen) {
        EndpointDialog(existing = null, onSave = { ep ->
            endpointStore().upsert(ep)
            endpoints.value = loadEndpoints()
            addEndpointOpen = false
            toast(ctx, "Endpoint saved — tap it to activate")
        }, onDismiss = { addEndpointOpen = false })
    }
    editingEndpoint?.let { current ->
        EndpointDialog(existing = current, onSave = { ep ->
            endpointStore().upsert(ep)
            endpoints.value = loadEndpoints()
            editingEndpoint = null
            toast(ctx, "Endpoint updated")
        }, onDismiss = { editingEndpoint = null })
    }
}

private fun endpointStore() = EndpointStore(
    { Global.prefs.cloudEndpointsJson },
    { Global.prefs.cloudEndpointsJson = it }
)

private fun loadEndpoints(): List<CloudEndpoint> = endpointStore().list()

@Composable
private fun EndpointDialog(
    existing: CloudEndpoint?,
    onSave: (CloudEndpoint) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "https://api.groq.com/openai/v1") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(if (existing == null) "Add cloud endpoint" else "Edit endpoint") },
        text = {
            Column {
                EndpointField(name, "name (e.g. Groq)") { name = it }
                EndpointField(baseUrl, "base URL (OpenAI-compatible)") { baseUrl = it }
                EndpointField(apiKey, "API key (stored encrypted on device)") { apiKey = it }
                EndpointField(model, "model (e.g. llama-3.3-70b-versatile)") { model = it }
                Text(
                    "Works with Groq · OpenRouter · Together · Ollama · LM Studio · any OpenAI-compatible server",
                    color = TextHint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (baseUrl.isNotBlank() && model.isNotBlank()) {
                    onSave(
                        CloudEndpoint(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "endpoint" },
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(), // EndpointStore encrypts before persisting
                            model = model.trim(),
                            headers = existing?.headers ?: emptyMap(),
                            maxTokens = existing?.maxTokens ?: 600
                        )
                    )
                }
            }) { Text("Save", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextHint) } }
    )
}

@Composable
private fun EndpointField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(hint, color = TextHint) },
        singleLine = true,
        colors = outlined(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

// ---------- LOOK ----------

@Composable
private fun LookScreen() {
    val ctx = LocalContext.current
    val prefs = Global.prefs
    val scope = rememberCoroutineScope()
    var busyFont by remember { mutableStateOf("") }
    val themes = remember { mutableStateOf(ThemeEngine.byId(prefs.themeId).id) }
    val fontId = remember { mutableStateOf(prefs.fontId) }
    val fonts = remember { mutableStateOf(Global.fonts.all()) }
    val keyHeight = remember { mutableStateOf(prefs.keyHeightDp) }
    val bgPath = remember { mutableStateOf(prefs.bgImagePath) }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // file/parcel IO off the main thread
                val result = withContext(Dispatchers.IO) { Global.fonts.importFromUri(uri) }
                result.onSuccess {
                    prefs.fontId = it
                    fontId.value = it
                    fonts.value = Global.fonts.all()
                }.onFailure { toast(ctx, "Could not load that font") }
            }
        }
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val dst = java.io.File(ctx.filesDir, "bg_${System.currentTimeMillis()}.jpg")
                        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                            dst.outputStream().use { ins.copyTo(it) }
                        }
                        dst.absolutePath
                    }
                }.onSuccess { p ->
                    prefs.bgImagePath = p
                    bgPath.value = p
                }.onFailure { toast(ctx, "Could not use that image") }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (Global.prefs.feature("themes")) {
            item { SectionTitle("Themes") }
            items(ThemeEngine.PRESETS) { t ->
                RowCard(
                    t.name,
                    "accent #" + Integer.toHexString(t.accent).takeLast(6),
                    trailing = { Box(Modifier.size(18.dp).background(Color(t.accent), RoundedCornerShape(9.dp))) },
                    onTap = { themes.value = t.id; Global.themes.apply(t.id) }
                )
            }
        }
        if (Global.prefs.feature("theme_codes")) {
            item {
                SectionTitle("Theme codes (share with friends)")
                RowCard("Paste a code", "bg=12101A;keys=1C1830;text=F1EEFF;accent=8B5CF6…", onTap = { })
                var code by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    placeholder = { Text("paste theme code", color = TextHint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = CardSpecial,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Accent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                RowCard("Apply code", null, onTap = {
                    if (Global.themes.addCode(code)) toast(ctx, "Theme applied ✓") else toast(ctx, "Invalid code")
                })
            }
        }
        if (Global.prefs.feature("theme_editor")) {
            item {
                // theme editor (#65): accent + corner radius, saved as a custom theme
                SectionTitle("Theme editor")
                RowCard("Accent color", "tap to cycle 8 curated accents", onTap = {
                    val accents = listOf(0xFF8B5CF6.toInt(), 0xFF34D399.toInt(), 0xFF38BDF8.toInt(),
                        0xFFF472B6.toInt(), 0xFFF59E0B.toInt(), 0xFFE11D48.toInt(),
                        0xFF22C55E.toInt(), 0xFF6366F1.toInt())
                    val base = ThemeEngine.byId(prefs.themeId)
                    val next = accents[(accents.indexOf(base.accent) + 1).mod(accents.size)]
                    Global.themes.saveCustom(base.copy(id = "custom_editor", accent = next))
                    toast(ctx, "Accent updated ✓")
                })
                RowCard("Corner radius", "cycles 10 → 26 dp", onTap = {
                    val base = ThemeEngine.byId(prefs.themeId)
                    val next = ((base.radiusDp - 10 + 4) % 17) + 10
                    Global.themes.saveCustom(base.copy(id = "custom_editor", radiusDp = next))
                    toast(ctx, "Radius ${next}dp ✓")
                })
            }
        }
        if (Global.prefs.feature("google_fonts")) {
            item {
                // Google Fonts catalog (#67): OFL fonts, direct download
                SectionTitle("Google Fonts catalog (download)")
            }
            items(FontCatalog.FONTS) { (id, label) ->
                RowCard(
                    label,
                    if (busyFont == label) "downloading…" else "OFL · tap to download & apply",
                    onTap = {
                        if (busyFont.isEmpty()) {
                            busyFont = label
                            scope.launch {
                                // NetworkOnMainThreadException guard — download on IO
                                val ok = withContext(Dispatchers.IO) {
                                    Global.fonts.download(id, label).getOrDefault(false)
                                }
                                busyFont = ""
                                if (ok) {
                                    prefs.fontId = id
                                    fontId.value = id
                                    fonts.value = Global.fonts.all()
                                    toast(ctx, "$label installed ✓")
                                } else toast(ctx, "Download failed — check network")
                            }
                        }
                    }
                )
            }
        }
        if (Global.prefs.feature("fonts")) {
            item { SectionTitle("Font") }
            items(fonts.value) { (id, label) ->
                RowCard(label, if (fontId.value == id) "✓ in use" else null, onTap = {
                    fontId.value = id; prefs.fontId = id
                })
            }
        }
        if (Global.prefs.feature("font_import")) {
            item {
                RowCard("Import .ttf / .otf", "Any font file from storage", onTap = {
                    fontPicker.launch(arrayOf("font/*", "application/octet-stream"))
                })
            }
        }
        item {
            SectionTitle("Keyboard size")
            RowCard("Key height", "${keyHeight.value} dp", trailing = {
                Slider(
                    value = keyHeight.value.toFloat(), onValueChange = {
                        keyHeight.value = it.toInt(); prefs.keyHeightDp = it.toInt()
                    },
                    valueRange = 40f..70f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    modifier = Modifier.width(160.dp)
                )
            })
            RowCard("One-handed mode", "current: ${prefs.oneHanded}", onTap = {
                prefs.oneHanded = when (prefs.oneHanded) { "off" -> "right"; "right" -> "left"; else -> "off" }
            })
            if (Global.prefs.feature("photo_keyboard")) {
                RowCard("Background image", if (bgPath.value.isEmpty()) "none" else "set ✓", onTap = {
                    bgPicker.launch(arrayOf("image/*"))
                })
            }
            if (Global.prefs.feature("blur_dim")) {
                RowCard("Background blur", "${Global.prefs.bgBlur.toInt()} / 16", trailing = {
                    Slider(
                        value = Global.prefs.bgBlur, onValueChange = { Global.prefs.bgBlur = it },
                        valueRange = 0f..16f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                        modifier = Modifier.width(160.dp)
                    )
                })
                RowCard("Background dim", "${(Global.prefs.bgDim * 100).toInt()}%", trailing = {
                    Slider(
                        value = Global.prefs.bgDim, onValueChange = { Global.prefs.bgDim = it },
                        valueRange = 0f..0.9f, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                        modifier = Modifier.width(160.dp)
                    )
                })
            }
        }
    }
}

// ---------- FEATURES ----------

@Composable
private fun FeaturesScreen() {
    val cats = com.infikey.prefs.Features.Category.entries
    LazyColumn(Modifier.fillMaxSize()) {
        cats.forEach { cat ->
            item { SectionTitle("${cat.label} · ${com.infikey.prefs.Features.ALL.count { it.category == cat }}") }
            items(com.infikey.prefs.Features.ALL.filter { it.category == cat }) { f ->
                var checked by remember { mutableStateOf(Global.prefs.feature(f.id, f.default)) }
                SwitchPref(f.name, f.desc, checked) {
                    checked = it
                    // central setter (Prefs.setFeature) syncs the typed accessors
                    Global.prefs.setFeature(f.id, it)
                }
            }
        }
    }
}

// ---------- ADVANCED ----------

@Composable
private fun AdvancedScreen() {
    val ctx = LocalContext.current
    val prefs = Global.prefs
    val scope = rememberCoroutineScope()
    val dna = remember { mutableStateOf(prefs.readStyleDna()) }
    val macros = remember { mutableStateOf(prefs.readMacros()) }
    var macroKey by remember { mutableStateOf("") }
    var macroVal by remember { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            val o = org.json.JSONObject(text)
            if (o.has("macros")) prefs.macrosJson = o.getString("macros")
            if (o.has("facts")) prefs.factsJson = o.getString("facts")
            if (o.has("style_dna")) prefs.styleDnaJson = o.getString("style_dna")
            if (o.has("themes")) {
                val a = o.getJSONArray("themes")
                for (i in 0 until a.length()) Global.themes.addCode(a.getString(i))
            }
            if (o.has("learned")) {
                val lo = o.getJSONObject("learned")
                lo.keys().asSequence().forEach { k ->
                    val arr = lo.getJSONArray(k)
                    prefs.sp.edit().putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet()).apply()
                }
            }
            macros.value = prefs.readMacros()
            dna.value = prefs.readStyleDna()
            toast(ctx, "Backup restored ✓")
        }.onFailure { toast(ctx, "Import failed: ${it.message}") }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (Global.prefs.feature("style_dna")) {
            item {
                SectionTitle("Style DNA — how the AI writes like you")
                DnaSlider("Formality", dna.value.formality) {
                    dna.value = dna.value.copy(formality = it)
                    prefs.writeStyleDna(dna.value)
                }
                DnaSlider("Energy", dna.value.energy) {
                    dna.value = dna.value.copy(energy = it)
                    prefs.writeStyleDna(dna.value)
                }
                DnaSlider("Brevity", dna.value.brevity) {
                    dna.value = dna.value.copy(brevity = it)
                    prefs.writeStyleDna(dna.value)
                }
            }
        }
        if (Global.prefs.feature("macros")) {
            item { SectionTitle("Macros — /shortcut → full text") }
            items(macros.value.entries.toList()) { (k, v) ->
                RowCard(k, v.take(60), trailing = {
                    Text("delete", color = Accent2, fontSize = 12.sp)
                }, onTap = {
                    macros.value = macros.value.toMutableMap().apply { remove(k) }
                    prefs.writeMacros(macros.value)
                })
            }
            item {
                RowCard("New macro", "trigger without /, e.g. 'addr'", onTap = { })
                OutlinedTextField(value = macroKey, onValueChange = { macroKey = it },
                    placeholder = { Text("trigger", color = TextHint) },
                    colors = outlined(), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                OutlinedTextField(value = macroVal, onValueChange = { macroVal = it },
                    placeholder = { Text("expansion", color = TextHint) },
                    colors = outlined(), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                RowCard("Save macro", null, onTap = {
                    if (macroKey.isNotBlank() && macroVal.isNotBlank()) {
                        macros.value = macros.value.toMutableMap().apply { put(macroKey, macroVal) }
                        prefs.writeMacros(macros.value)
                        macroKey = ""; macroVal = ""
                    }
                })
            }
        }
        item {
            SectionTitle("Privacy")
            SwitchPref("Per-app language memory", "Remembers each app's keyboard language", prefs.perAppAuto) {
                prefs.perAppAuto = it
            }
            SwitchPref("Clipboard history", "Kept locally, sensitive rows hidden", prefs.clipboardHistoryEnabled) {
                prefs.clipboardHistoryEnabled = it
            }
            RowCard("Clear clipboard history now", null, onTap = {
                Global.clipboard.clearAll()
            })
        }
        item {
            // my facts (#17): powers Field Genius chips + {{}} templates
            SectionTitle("My facts — form auto-fill & templates")
            var fName by remember { mutableStateOf(prefs.facts()["name"] ?: "") }
            var fEmail by remember { mutableStateOf(prefs.facts()["email"] ?: "") }
            var fPhone by remember { mutableStateOf(prefs.facts()["phone"] ?: "") }
            var fAddr by remember { mutableStateOf(prefs.facts()["address"] ?: "") }
            OutlinedTextField(value = fName, onValueChange = { fName = it; prefs.writeFact("name", it) },
                placeholder = { Text("full name", color = TextHint) }, colors = outlined(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            OutlinedTextField(value = fEmail, onValueChange = { fEmail = it; prefs.writeFact("email", it) },
                placeholder = { Text("email", color = TextHint) }, colors = outlined(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            OutlinedTextField(value = fPhone, onValueChange = { fPhone = it; prefs.writeFact("phone", it) },
                placeholder = { Text("phone", color = TextHint) }, colors = outlined(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            OutlinedTextField(value = fAddr, onValueChange = { fAddr = it; prefs.writeFact("address", it) },
                placeholder = { Text("address", color = TextHint) }, colors = outlined(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        }
        item {
            // security & trust (#62–#66, #77, #85)
            SectionTitle("Security & data")
            SwitchPref("Biometric lock", "Fingerprint/face required to open settings", prefs.biometricLock) {
                prefs.biometricLock = it
            }
            RowCard("Data audit",
                "${Global.prefs.learnedWordCount()} learned words · AI log entries: " +
                    "${runCatching { org.json.JSONArray(prefs.aiLogJson).length() }.getOrDefault(0)} · nothing leaves this device unless you add a cloud API",
                onTap = { })
            RowCard("Wipe learned words & per-app memory", null, onTap = {
                Global.prefs.wipeLearnedWords()
                Global.suggestions.loadForLayout(prefs.activeLayoutId)
                toast(ctx, "Learning wiped ✓")
            })
            RowCard("Clear AI transparency log", null, onTap = { prefs.aiLogJson = "[]" })
            RowCard("Export backup", "themes, macros, facts, learned words → file", onTap = {
                exportBackup(ctx)
            })
            RowCard("Import backup", "pick infikey-backup.json", onTap = {
                importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            })
            RowCard("Battery saver", "glow, trails and idle effects pause automatically in system saver", onTap = { })
            RowCard("Safe apps (always incognito)", prefs.safeApps.joinToString(", ").ifEmpty { "none yet" }, onTap = { })
            var safeApp by remember { mutableStateOf("") }
            OutlinedTextField(value = safeApp, onValueChange = { safeApp = it },
                placeholder = { Text("package name, e.g. net.powergroup.media", color = TextHint) },
                colors = outlined(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            RowCard("Add safe app", null, onTap = {
                if (safeApp.isNotBlank()) {
                    prefs.safeApps = (prefs.safeApps + safeApp.trim()).toMutableSet()
                    safeApp = ""
                    toast(ctx, "Safe app added ✓")
                }
            })
        }
        item {
            SectionTitle("About")
            RowCard("InfiKey 1.0.0", "On-device AI keyboard · MIT license", onTap = { })
            RowCard("GitHub repo", "github.com/mohammedaliyan07/InfiKey", onTap = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mohammedaliyan07/InfiKey")))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            })
        }
    }
}

// ---------- backup / security helpers ----------

private fun exportBackup(ctx: Context) {
    runCatching {
        val prefs = Global.prefs
        val o = org.json.JSONObject()
            .put("macros", prefs.macrosJson)
            .put("facts", prefs.factsJson)
            .put("style_dna", prefs.styleDnaJson)
            .put("themes", org.json.JSONArray(Global.themes.savedCodes().toList()))
            .put("learned", org.json.JSONObject().apply {
                prefs.sp.all.entries.filter { it.key.startsWith("learned_") }.forEach { e ->
                    val words: List<String> = (e.value as? Set<*>)?.map { "$it" } ?: listOf()
                    put(e.key, org.json.JSONArray(words))
                }
            })
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = java.io.File(dir, "infikey-backup.json")
        f.writeText(o.toString())
        toast(ctx, "Saved: ${f.absolutePath}")
    }.onFailure { toast(ctx, "Export failed: ${it.message}") }
}

private fun unlockWithBiometric(ctx: Context, onOk: () -> Unit) {
    runCatching {
        val pm = BiometricManager.from(ctx)
        if (pm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) { onOk(); return }
        val prompt = BiometricPrompt(ctx as FragmentActivity,
            ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock InfiKey")
                .setNegativeButtonText("Cancel")
                .build()
        )
    }.onFailure { onOk() } // biometric unavailable → fail open
}

object FontCatalog {
    val FONTS = listOf(
        "inter" to "Inter", "poppins" to "Poppins", "rubik" to "Rubik",
        "nunito" to "Nunito", "raleway" to "Raleway", "space-grotesk" to "Space Grotesk",
        "outfit" to "Outfit", "playfair-display" to "Playfair Display",
        "merriweather" to "Merriweather", "lora" to "Lora", "cinzel" to "Cinzel",
        "bebas-neue" to "Bebas Neue", "oswald" to "Oswald", "lobster" to "Lobster",
        "pacifico" to "Pacifico", "dancing-script" to "Dancing Script",
        "caveat" to "Caveat", "great-vibes" to "Great Vibes", "orbitron" to "Orbitron",
        "jetbrains-mono" to "JetBrains Mono", "fira-code" to "Fira Code",
        "source-code-pro" to "Source Code Pro", "quicksand" to "Quicksand",
        "comfortaa" to "Comfortaa"
    )
}

@Composable
private fun DnaSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    RowCard(label, "${v.toInt()}", trailing = {
        Slider(
            value = v, onValueChange = { v = it; onChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.width(160.dp)
        )
    })
}

@Composable
private fun outlined() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = CardSpecial,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    cursorColor = Accent
)
